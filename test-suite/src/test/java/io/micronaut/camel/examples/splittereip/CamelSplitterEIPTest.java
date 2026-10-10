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
package io.micronaut.camel.examples.splittereip;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Ports the {@code splitter-eip} example of camel-spring-boot-examples.
 * <p>
 * The route is a Micronaut singleton. {@code camel.main.load-type-converters} was dropped from the
 * configuration as the example has no generated type converter loader, and the test uses JUnit
 * {@code assertInstanceOf} instead of the camel-test-junit6 {@code assertIsInstanceOf}.
 */
@MicronautTest(environments = "example-splitter-eip")
public class CamelSplitterEIPTest {

	@Inject
	CamelContext camelContext;

	@Inject
	ProducerTemplate producerTemplate;

	@Test
	public void testSplit() throws InterruptedException {
		MockEndpoint mockA = camelContext.getEndpoint("mock:a", MockEndpoint.class);

		mockA.expectedBodiesReceived("A", "B", "C");

		producerTemplate.sendBody("direct:splitter", new ArrayList<>(Arrays.asList("A", "B", "C")));

		mockA.assertIsSatisfied();
	}

	@Test
	public void testSplitAggregate() throws InterruptedException {
		MockEndpoint mockB = camelContext.getEndpoint("mock:b", MockEndpoint.class);
		MockEndpoint mockC = camelContext.getEndpoint("mock:c", MockEndpoint.class);

		mockB.expectedBodiesReceived("Alpha", "Beta", "Charlie");
		mockC.expectedBodiesReceived("Alpha+Beta+Charlie");

		producerTemplate.sendBody("direct:split-aggregate", "A,B,C");

		mockB.assertIsSatisfied();
		mockC.assertIsSatisfied();
	}

	@Test
	public void testSplitAggregatePojo() throws InterruptedException {
		MockEndpoint mockD = camelContext.getEndpoint("mock:d", MockEndpoint.class);
		MockEndpoint mockE = camelContext.getEndpoint("mock:e", MockEndpoint.class);

		mockD.expectedBodiesReceived("Alpha", "Beta", "Charlie");
		mockE.expectedBodiesReceived("Alpha+Beta+Charlie");

		producerTemplate.sendBody("direct:split-aggregate-bean", "A,B,C");

		mockD.assertIsSatisfied();
		mockE.assertIsSatisfied();
	}

	@Test
	public void testSplitAggregateStopOnException() throws InterruptedException {
		MockEndpoint mockF = camelContext.getEndpoint("mock:f", MockEndpoint.class);
		MockEndpoint mockG = camelContext.getEndpoint("mock:g", MockEndpoint.class);

		mockF.expectedBodiesReceived("Alpha");
		mockG.expectedMessageCount(0);

		final CamelExecutionException ex = assertThrows(CamelExecutionException.class,
				() -> producerTemplate.sendBody("direct:split-aggregate-stop-on-exception", "A,E,C"));

		CamelExchangeException cee = assertInstanceOf(CamelExchangeException.class, ex.getCause());
		IllegalArgumentException iae = assertInstanceOf(IllegalArgumentException.class, cee.getCause());
		assertEquals("Unknown key E", ex.getCause().getCause().getMessage());

		mockF.assertIsSatisfied();
		mockG.assertIsSatisfied();
	}

	@Test
	public void testSplitAggregateStopOnAggregationException() throws InterruptedException {
		MockEndpoint mockH = camelContext.getEndpoint("mock:h", MockEndpoint.class);
		MockEndpoint mockJ = camelContext.getEndpoint("mock:j", MockEndpoint.class);

		// without error aggregation is done as usual
		// Messages are transformed by MyMessageTransformer before reaching mock:h
		mockH.expectedBodiesReceived("Alpha", "Beta", "Charlie");
		mockJ.expectedBodiesReceived("Alpha+Beta+Charlie");

		producerTemplate.sendBody("direct:split-aggregate-stop-on-aggregation-exception", "A,B,C");

		mockH.assertIsSatisfied();
		mockJ.assertIsSatisfied();

		mockH.reset();
		mockJ.reset();

		// 'E' is not in the transformer dictionary, so it throws IllegalArgumentException.
		// With .continued(true), the exception from the transformer is swallowed and the original
		// body 'E' passes through to mock:h. Then the aggregation strategy also throws on 'E',
		// which stops the split — so 'C' and 'D' are never processed.
		mockH.expectedBodiesReceived("Alpha", "Beta", "E");
		// The aggregation result is the original unsplit input since aggregation failed
		mockJ.expectedBodiesReceived("A,B,E,C,D");

		// 'E' is poison message which will lead to an error during aggregation
		producerTemplate.sendBody("direct:split-aggregate-stop-on-aggregation-exception", "A,B,E,C,D");

		mockH.assertIsSatisfied();
		mockJ.assertIsSatisfied();
	}
}
