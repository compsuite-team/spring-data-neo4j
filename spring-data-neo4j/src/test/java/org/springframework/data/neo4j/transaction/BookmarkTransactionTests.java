/*
 * Copyright 2011-2019 the original author or authors.
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

package org.springframework.data.neo4j.transaction;

import static org.assertj.core.api.Assertions.*;
import static org.junit.Assume.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.Collection;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.neo4j.driver.v1.AccessMode;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.GraphDatabase;
import org.neo4j.ogm.drivers.bolt.driver.BoltDriver;
import org.neo4j.ogm.session.SessionFactory;
import org.neo4j.ogm.testutil.MultiDriverTestClass;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.data.neo4j.annotation.EnableBookmarkManagement;
import org.springframework.data.neo4j.bookmark.BookmarkManager;
import org.springframework.data.neo4j.bookmark.CaffeineBookmarkManager;
import org.springframework.data.neo4j.examples.movies.domain.User;
import org.springframework.data.neo4j.examples.movies.service.UserService;
import org.springframework.data.neo4j.repository.config.EnableNeo4jRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * @author Frantisek Hartman
 */
@ContextConfiguration(classes = { BookmarkTransactionTests.BookmarkConfiguration.class })
@RunWith(SpringJUnit4ClassRunner.class)
public class BookmarkTransactionTests extends MultiDriverTestClass {

	@Autowired private BookmarkManager bookmarkManager;

	@Autowired private Driver nativeDriver;

	@Autowired private UserService userService;

	@BeforeClass
	public static void setUpClass() throws Exception {
		assumeTrue(getBaseConfiguration().build().getDriverClassName().equals(BoltDriver.class.getName()));
	}

	@Test
	public void operationsShouldStoreBookmarkAndUseBookmarkShouldReuseBookmark() throws Exception {
		userService.saveWithTxAnnotationOnInterface(new User());

		Collection<String> bookmarks = bookmarkManager.getBookmarks();
		assertThat(bookmarks).isNotEmpty();

		Collection<User> users = userService.getAllUsersWithBookmark();

		Mockito.verify(nativeDriver).session(any(AccessMode.class), eq(bookmarks));
	}

	@Test
	public void operationsShouldReplaceOlderBookmarks() throws Exception {
		userService.saveWithTxAnnotationOnInterface(new User());
		Collection<String> bookmarks = bookmarkManager.getBookmarks();
		assertThat(bookmarks).isNotEmpty();

		userService.saveWithTxAnnotationOnInterface(new User());
		userService.getAllUsersWithBookmark();

		assertThat(bookmarkManager.getBookmarks()).isNotEmpty();
		assertThat(bookmarkManager.getBookmarks()).doesNotContainAnyElementsOf(bookmarks);
	}

	@Configuration
	@EnableTransactionManagement
	@ComponentScan(value = { "org.springframework.data.neo4j.examples.movies.service" })
	@EnableNeo4jRepositories("org.springframework.data.neo4j.examples.movies.repo")
	@EnableBookmarkManagement
	static class BookmarkConfiguration {

		@Bean
		public PlatformTransactionManager transactionManager() {
			return new Neo4jTransactionManager(sessionFactory());
		}

		@Bean
		@Scope
		public BookmarkManager bookmarkManager() {
			return new CaffeineBookmarkManager();
		}

		@Bean
		public Driver nativeDriver() {
			return spy(GraphDatabase.driver(getBaseConfiguration().build().getURI()));
		}

		@Bean
		public SessionFactory sessionFactory() {
			return new SessionFactory(new BoltDriver(nativeDriver()),
					"org.springframework.data.neo4j.examples.movies.domain");
		}

	}
}
