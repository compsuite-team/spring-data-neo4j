/*
 * Copyright 2011-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.neo4j.integration.issues.gh2493;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.Transaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.neo4j.config.AbstractNeo4jConfig;
import org.springframework.data.neo4j.core.DatabaseSelectionProvider;
import org.springframework.data.neo4j.core.transaction.Neo4jBookmarkManager;
import org.springframework.data.neo4j.core.transaction.Neo4jTransactionManager;
import org.springframework.data.neo4j.repository.config.EnableNeo4jRepositories;
import org.springframework.data.neo4j.test.BookmarkCapture;
import org.springframework.data.neo4j.test.Neo4jExtension;
import org.springframework.data.neo4j.test.Neo4jIntegrationTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * @author Michael J. Simons
 */
@Neo4jIntegrationTest
public class Gh2493IT {

	protected static Neo4jExtension.Neo4jConnectionSupport neo4jConnectionSupport;

	@BeforeAll
	protected static void setupData(@Autowired BookmarkCapture bookmarkCapture) {

		try (Session session = neo4jConnectionSupport.getDriver().session(bookmarkCapture.createSessionConfig());
				Transaction transaction = session.beginTransaction();
		) {
			transaction.run("MATCH (n) DETACH DELETE n").consume();
			transaction.commit();
			bookmarkCapture.seedWith(session.lastBookmark());
		}
	}

	@Test
	void saveOneShouldWork(@Autowired Driver driver, @Autowired BookmarkCapture bookmarkCapture,
			@Autowired TestObjectRepository repository) {

		TestObject testObject = new TestObject(new TestData(4711, "Foobar"));
		testObject = repository.save(testObject);

		assertThat(testObject.getId()).isNotNull();
		assertThatTestObjectHasBeenCreated(driver, bookmarkCapture, testObject);
	}

	@Test
	void saveAllShouldWork(@Autowired Driver driver, @Autowired BookmarkCapture bookmarkCapture,
			@Autowired TestObjectRepository repository) {

		TestObject testObject = new TestObject(new TestData(4711, "Foobar"));
		testObject = repository.saveAll(Collections.singletonList(testObject)).get(0);

		assertThat(testObject.getId()).isNotNull();
		assertThatTestObjectHasBeenCreated(driver, bookmarkCapture, testObject);
	}

	private static void assertThatTestObjectHasBeenCreated(Driver driver, BookmarkCapture bookmarkCapture,
			TestObject testObject) {
		try (Session session = driver.session(bookmarkCapture.createSessionConfig())) {
			Map<String, Object> arguments = new HashMap<>();
			arguments.put("id", testObject.getId());
			arguments.put("num", testObject.getData().getNum());
			arguments.put("string", testObject.getData().getString());
			long cnt = session.run(
							"MATCH (n:TestObject) WHERE n.id = $id AND n.dataNum = $num AND n.dataString = $string RETURN count(n)",
							arguments)
					.single().get(0).asLong();
			assertThat(cnt).isOne();
		}
	}

	@Configuration
	@EnableTransactionManagement
	@EnableNeo4jRepositories
	static class Config extends AbstractNeo4jConfig {

		@Bean
		public Driver driver() {

			return neo4jConnectionSupport.getDriver();
		}

		@Bean
		public BookmarkCapture bookmarkCapture() {
			return new BookmarkCapture();
		}

		@Override
		public PlatformTransactionManager transactionManager(Driver driver,
				DatabaseSelectionProvider databaseNameProvider) {

			BookmarkCapture bookmarkCapture = bookmarkCapture();
			return new Neo4jTransactionManager(driver, databaseNameProvider,
					Neo4jBookmarkManager.create(bookmarkCapture));
		}
	}
}
