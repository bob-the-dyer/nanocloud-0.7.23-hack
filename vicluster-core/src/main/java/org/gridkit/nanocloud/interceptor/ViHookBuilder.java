/**
 * Copyright 2013 Alexey Ragozin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gridkit.nanocloud.interceptor;

import java.io.Serializable;
import java.rmi.Remote;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.gridkit.lab.interceptor.CutPoint;
import org.gridkit.lab.interceptor.Interception;
import org.gridkit.lab.interceptor.Interceptor;
import org.gridkit.util.concurrent.BlockingBarrier;
import org.gridkit.util.concurrent.zerormi.ExportableBarrier;
import org.gridkit.vicluster.ViConfigurable;
import org.gridkit.vicluster.isolate.Isolate;

public class ViHookBuilder {

	public static Builder newCallSiteHook(Interceptor interceptor) {
		return new Builder(interceptor);
	}

	public static Builder newCallSiteHook() {
		return new Builder(null);
	}
	

	private static Map<String, String> SIG_TYPE_MAP = new HashMap<String, String>();
	static {
		SIG_TYPE_MAP.put("void", "V");
		SIG_TYPE_MAP.put("byte", "B");
		SIG_TYPE_MAP.put("char", "C");
		SIG_TYPE_MAP.put("double", "D");
		SIG_TYPE_MAP.put("float", "F");
		SIG_TYPE_MAP.put("int", "I");
		SIG_TYPE_MAP.put("long", "J");
		SIG_TYPE_MAP.put("short", "S");
		SIG_TYPE_MAP.put("boolean", "Z");
	}
	
	private static String toSigName(String type) {
		if (SIG_TYPE_MAP.containsKey(type)) {
			type = SIG_TYPE_MAP.get(type);
		}
		if (type.length() > 1 && type.charAt(0) != '[') {
			type = "L" + type + ";";
		}
		return type.replace('.', '/');
	}
	
	public static class Builder {
	
		private Interceptor interceptor;
		
		private Set<Class<?>> targetClasses = new HashSet<Class<?>>();
		private String methodName;
		private Class<?>[] methodSignature;
		
		private ParamBasedCallFilter filter;
		
		private Builder(Interceptor interceptor) {
			this.interceptor = interceptor;
		}

		public Builder onTypes(Class<?>... types) {
			targetClasses.addAll(Arrays.asList(types));
			return this;
		}
		
		public Builder onMethod(String name) {
			if (methodName != null) {
				throw new IllegalStateException("Method name is already set");
			}
			methodName = name;
			return this;
		}

		public Builder onMethod(String name, Class<?>... paramtypes) {
			if (methodName != null) {
				throw new IllegalStateException("Method name is already set");
			}
			methodName = name;
			methodSignature = paramtypes;
			return this;
		}
		
		public Builder matchParams(Object... values) {
			if (filter == null) {
				filter = new ParamBasedCallFilter();
			}
			for(int i = 0; i != values.length; ++i) {
				filter.addParamMatcher(i, new LiteralMatcher(values[i]));
			}
			return this;
		}

		public Builder matchParam(int n, Object value) {
			if (filter == null) {
				filter = new ParamBasedCallFilter();
			}
			filter.addParamMatcher(n, new LiteralMatcher(value));
			return this;
		}
		
		public Builder doBarrier(BlockingBarrier barrier) {
			if (interceptor != null) {
				throw new IllegalArgumentException("Interceptor or interception action is already set");
			}
			if (!(barrier instanceof ExportableBarrier)) {
				barrier = new ExportableBarrier(barrier);
			}
			interceptor = new BarrierStub(barrier);
			return this;
		}
		
		public Builder doReturn(Object value) {
			if (interceptor != null) {
				throw new IllegalArgumentException("Interceptor or interception action is already set");
			}
			interceptor = new ReturnValueStub(value);
			return this;
		}

		public Builder doThrow(Throwable e) {
			if (interceptor != null) {
				throw new IllegalArgumentException("Interceptor or interception action is already set");
			}
			interceptor = new ThrowStub(e);
			return this;
		}
		
		public Builder doCount(AtomicLong counter) {
			if (interceptor != null) {
				throw new IllegalArgumentException("Interceptor or interception action is already set");
			}
			interceptor = new CounterStub(counter);
			return this;
		}

		public void apply(ViConfigurable node) {
			if (interceptor == null) {
				throw new IllegalArgumentException("Interceptor or interception action is required");
			}
			else if (targetClasses.isEmpty() && methodName == null) {
				throw new IllegalArgumentException("You should specify at least target class or method name");
			}
			else {
				CallSiteCutPoint cp = new CallSiteCutPoint(makeClassNames(), methodName, makeSignature());
				InstrumentationHookRule rule = new InstrumentationHookRule(cp, makeInterceptor());
				node.addStartupHook(rule.toString(), rule);
			}
		}
		
		private Interceptor makeInterceptor() {
			if (filter != null) {
				return new FilterableInterceptor(filter, interceptor);
			}
			else {
				return interceptor;
			}
		}

		private String[] makeClassNames() {
			if (targetClasses.isEmpty()) {
				return null;
			}
			String[] classnames = new String[targetClasses.size()];
			int n = 0;
			Iterator<Class<?>> it = targetClasses.iterator();
			while(it.hasNext()) {
				classnames[n++] = it.next().getName().replace('.', '/');
			}
			Arrays.sort(classnames);
			return classnames;
		}

		private String[] makeSignature() {
			if (methodSignature == null) {
				return null;
			}
			String[] sig = new String[methodSignature.length];
			for(int i = 0; i != methodSignature.length; ++i) {
				Class<?> t = methodSignature[i];
				if (t != null) {
					sig[i] = toSigName(t.getName());
				}
			}
			return sig;
		}
	}
	
	public static interface ParamMatcher extends Serializable {
		public boolean matches(Object param);
	}
	
	private static class InstrumentationHookRule implements Runnable, Serializable {

		private static final long serialVersionUID = 20130622L;

		private CutPoint cutPoint;
		private InterceptorSource source;

		public InstrumentationHookRule(CutPoint cutPoint, final Interceptor interceptor) {
			this.cutPoint = cutPoint;
			this.source = new RemotableSource(interceptor);
		}

		@Override
		public void run() {
			Isolate isolate = Isolate.currentIsolate();
			if (isolate == null) {
				throw new IllegalArgumentException("Not in isolate");
			}
			isolate.addInstrumenationRule(cutPoint, new LazyInterceptor(source));
		}
	}
	
	private static class LazyInterceptor implements Interceptor {

		private InterceptorSource source;
		private volatile Interceptor interceptor;
		
		public LazyInterceptor(InterceptorSource source) {
			this.source = source;
		}

		@Override
		public void handle(Interception hook) {
			if (interceptor == null) {
				synchronized (this) {
					if (interceptor == null) {
						interceptor = source.getInterceptor();
					}
				}
			}			
			interceptor.handle(hook);
		}
	}
	
	private static interface InterceptorSource extends Remote {
		
		public Interceptor getInterceptor();
		
	}

	/**
	 * Serialization is implemented as fallback mechanism
	 * for cases there ZeroRMI is not used.
	 */
	private static class RemotableSource implements InterceptorSource, Serializable {

		private static final long serialVersionUID = 20130622L;
		
		private final Interceptor interceptor;
	
		private RemotableSource(Interceptor interceptor) {
			this.interceptor = interceptor;
		}
	
		@Override
		public Interceptor getInterceptor() {
			return interceptor;
		}
	}
}
