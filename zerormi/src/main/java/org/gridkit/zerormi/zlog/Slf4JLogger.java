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
package org.gridkit.zerormi.zlog;

import org.gridkit.zerormi.zlog.Slf4JStream.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


class Slf4JLogger implements ZLogger {

	private final String root;
	
	public Slf4JLogger() {
		this.root = "";
	}

	public Slf4JLogger(String root) {
		this.root = root;
	}

	@Override
	public ZLogger getLogger(String path) {
		String ln = root;
		if (path != null && path.length() > 0) {
			ln += (ln.length() == 0 ? "" : ".") + path;
		}
		return new Slf4JLogger(ln);
	}
	
	@Override
	public LogStream get(String path, LogLevel level) {
		String ln = root;
		if (path != null && path.length() > 0) {
			ln += (ln.length() == 0 ? "" : ".") + path;
		}
		Logger l = LoggerFactory.getLogger(ln);
		switch(level) {
		case TRACE: return new Slf4JStream(l, Level.TRACE);
		case DEBUG: return new Slf4JStream(l, Level.DEBUG);
		case VERBOSE: return new Slf4JStream(l, Level.DEBUG);
		case INFO: return new Slf4JStream(l, Level.INFO);
		case CRITICAL: return new Slf4JStream(l, Level.ERROR);
		case FATAL: return new Slf4JStream(l, Level.ERROR);		
		}
		return new PrintStreamLogStream("", null, false);
	}
}
