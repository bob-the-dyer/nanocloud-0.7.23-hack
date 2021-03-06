/**
 * Copyright 2012 Alexey Ragozin
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
package org.gridkit.vicluster.telecontrol.ssh;

import org.gridkit.internal.com.jcraft.jsch.JSchException;
import org.gridkit.internal.com.jcraft.jsch.Session;

/**
 * This class encapsulate credentials and network configuration.
 * One provide could handle multiple hosts.
 * 
 * TODO add optional account name as paramater.
 * 
 * @author Alexey Ragozin (alexey.ragozin@gmail.com)
 */
public interface SshSessionFactory {

	public Session getSession(String host, String account) throws JSchException;
	
}
