/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.camel.examples.loadbalancereip;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.processor.loadbalancer.LoadBalancerSupport;

public class CustomLoadBalancer  extends LoadBalancerSupport {
	@Override
	public boolean process(Exchange exchange, AsyncCallback callback) {
		String body = exchange.getIn().getBody(String.class);
		try {
			if ("AE".contains(body)){
				getProcessors().get(0).process(exchange);
			} else if ("BCD".contains(body))
				getProcessors().get(1).process(exchange);
		}
		catch (Exception e) {
			exchange.setException(e);
		}
		callback.done(true);
		return true;
	}
}