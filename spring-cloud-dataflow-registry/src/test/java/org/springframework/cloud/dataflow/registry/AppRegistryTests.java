/*
 * Copyright 2017 the original author or authors.
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

package org.springframework.cloud.dataflow.registry;

import java.net.URI;
import java.util.List;

import org.hamcrest.Matchers;
import org.junit.Test;

import org.springframework.cloud.dataflow.core.ApplicationType;
import org.springframework.cloud.dataflow.registry.domain.AppRegistration;
import org.springframework.cloud.dataflow.registry.support.AppResourceCommon;
import org.springframework.cloud.dataflow.registry.support.NoSuchAppRegistrationException;
import org.springframework.cloud.deployer.resource.maven.MavenProperties;
import org.springframework.cloud.deployer.resource.registry.InMemoryUriRegistry;
import org.springframework.cloud.deployer.resource.registry.UriRegistry;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Unit tests for {@link AppRegistry}.
 *
 * @author Eric Bottard
 * @author Ilayaperumal Gopinathan
 */
@SuppressWarnings("unchecked")
public class AppRegistryTests {

	private UriRegistry uriRegistry = new InMemoryUriRegistry();

	private ResourceLoader resourceLoader = new DefaultResourceLoader();

	private AppRegistry appRegistry = new AppRegistry(uriRegistry, new AppResourceCommon(new MavenProperties(), resourceLoader));

	@Test
	public void testNotFound() {
		AppRegistration registration = appRegistry.find("foo", ApplicationType.source);
		assertThat(registration, Matchers.nullValue());
	}

	@Test
	public void testFound() {
		uriRegistry.register("source.foo", URI.create("classpath:/foo"));
		uriRegistry.register("source.foo.metadata", URI.create("classpath:/foo-metadata"));

		AppRegistration registration = appRegistry.find("foo", ApplicationType.source);
		assertThat(registration.getName(), is("foo"));
		assertThat(registration.getType(), is(ApplicationType.source));
	}

	@Test
	public void testMetadataResouceResolvesWhenAvailable() {
		uriRegistry.register("source.foo", URI.create("classpath:/foo"));
		uriRegistry.register("source.foo.metadata", URI.create("classpath:/foo-metadata"));

		AppRegistration registration = appRegistry.find("foo", ApplicationType.source);
		Resource appMetadataResource = appRegistry.getAppMetadataResource(registration);

		assertThat(appMetadataResource.getFilename(), is("foo-metadata"));
	}

	@Test
	public void testMetadataResouceNotAvailableResolvesToMainResource() {
		uriRegistry.register("source.foo", URI.create("classpath:/foo"));

		AppRegistration registration = appRegistry.find("foo", ApplicationType.source);
		Resource appMetadataResource = appRegistry.getAppMetadataResource(registration);

		assertThat(appMetadataResource.getFilename(), is("foo"));
	}

	@Test
	public void testFindAll() {
		uriRegistry.register("source.foo", URI.create("classpath:/foo-source"));
		uriRegistry.register("sink.foo", URI.create("classpath:/foo-sink"));
		uriRegistry.register("source.foo.metadata", URI.create("classpath:/foo-source-metadata"));
		uriRegistry.register("source.bar", URI.create("classpath:/bar-source"));
		uriRegistry.register("source.bar.metadata", URI.create("classpath:/bar-source-metadata"));

		List<AppRegistration> registrations = appRegistry.findAll();

		assertThat(registrations, containsInAnyOrder(
				allOf(hasProperty("name", is("foo")), hasProperty("uri", is(URI.create("classpath:/foo-source"))),
						hasProperty("metadataUri", is(URI.create("classpath:/foo-source-metadata"))),
						hasProperty("type", is(ApplicationType.source))),
				allOf(hasProperty("name", is("bar")), hasProperty("uri", is(URI.create("classpath:/bar-source"))),
						hasProperty("metadataUri", is(URI.create("classpath:/bar-source-metadata"))),
						hasProperty("type", is(ApplicationType.source))),
				allOf(hasProperty("name", is("foo")), hasProperty("uri", is(URI.create("classpath:/foo-sink"))),
						hasProperty("metadataUri", nullValue()), hasProperty("type", is(ApplicationType.sink)))));
	}

	@Test
	public void testFindAllPageable() {
		uriRegistry.register("source.foo", URI.create("classpath:/foo-source"));
		uriRegistry.register("sink.foo", URI.create("classpath:/foo-sink"));
		uriRegistry.register("source.foo.metadata", URI.create("classpath:/foo-source-metadata"));
		uriRegistry.register("source.bar", URI.create("classpath:/bar-source"));
		uriRegistry.register("source.bar.metadata", URI.create("classpath:/bar-source-metadata"));

		PageRequest pageRequest1 = new PageRequest(0, 2);
		Page<AppRegistration> registrations1 = appRegistry.findAll(pageRequest1);
		assertTrue(registrations1.getTotalElements() == 3);
		assertTrue(registrations1.getContent().size() == 2);
		assertTrue(registrations1.getContent().get(0).getName().equals("bar"));
		assertTrue(registrations1.getContent().get(1).getName().equals("foo"));
		PageRequest pageRequest2 = new PageRequest(1, 2);
		Page<AppRegistration> registrations2 = appRegistry.findAll(pageRequest2);
		assertTrue(registrations2.getTotalElements() == 3);
		assertTrue(registrations2.getContent().size() == 1);
		assertTrue(registrations2.getContent().get(0).getName().equals("foo"));
	}

	@Test
	public void testSave() {
		appRegistry.save("foo", ApplicationType.source, URI.create("classpath:/foo"), URI.create("foo-metadata"));

		AppRegistration registration = appRegistry.find("foo", ApplicationType.source);

		assertThat(registration.getName(), is("foo"));
		assertThat(registration.getType(), is(ApplicationType.source));
	}

	@Test
	public void testSaveApp() {
		appRegistry.save("fooApp", ApplicationType.app, URI.create("classpath:/foo"), URI.create("foo-metadata"));

		AppRegistration registration = appRegistry.find("fooApp", ApplicationType.app);

		assertThat(registration.getName(), is("fooApp"));
		assertThat(registration.getType(), is(ApplicationType.app));
	}

	@Test
	public void testImportAll() {
		// pre-register an app
		appRegistry.save("foo", ApplicationType.source, URI.create("classpath:/previous-foo-source"), null);

		appRegistry.importAll(false, new ClassPathResource("AppRegistryTests-importAll.properties", getClass()));
		List<AppRegistration> registrations = appRegistry.findAll();

		assertThat(registrations,
				containsInAnyOrder(allOf(hasProperty("name", is("foo")),
						hasProperty("uri", is(URI.create("classpath:/previous-foo-source"))),
						hasProperty("metadataUri", nullValue()), hasProperty("type", is(ApplicationType.source))),
						allOf(hasProperty("name", is("bar")),
								hasProperty("uri", is(URI.create("classpath:/bar-source"))),
								hasProperty("metadataUri", is(URI.create("classpath:/bar-source-metadata"))),
								hasProperty("type", is(ApplicationType.source))),
						allOf(hasProperty("name", is("foo")), hasProperty("uri", is(URI.create("classpath:/foo-sink"))),
								hasProperty("metadataUri", nullValue()), hasProperty("type", is(ApplicationType.sink)))));

		// Now import with overwrite = true
		appRegistry.importAll(true, new ClassPathResource("AppRegistryTests-importAll.properties", getClass()));
		registrations = appRegistry.findAll();

		assertThat(registrations, containsInAnyOrder(
				allOf(hasProperty("name", is("foo")), hasProperty("uri", is(URI.create("classpath:/foo-source"))),
						hasProperty("metadataUri", is(URI.create("classpath:/foo-source-metadata"))),
						hasProperty("type", is(ApplicationType.source))),
				allOf(hasProperty("name", is("bar")), hasProperty("uri", is(URI.create("classpath:/bar-source"))),
						hasProperty("metadataUri", is(URI.create("classpath:/bar-source-metadata"))),
						hasProperty("type", is(ApplicationType.source))),
				allOf(hasProperty("name", is("foo")), hasProperty("uri", is(URI.create("classpath:/foo-sink"))),
						hasProperty("metadataUri", nullValue()), hasProperty("type", is(ApplicationType.sink)))));
	}

	@Test
	public void testDelete() {
		// pre-register an app
		appRegistry.save("foo", ApplicationType.source, URI.create("classpath:/previous-foo-source"), null);
		assertThat(appRegistry.find("foo", ApplicationType.source), notNullValue());

		appRegistry.delete("foo", ApplicationType.source);
		assertThat(appRegistry.find("foo", ApplicationType.source), nullValue());

		try {
			appRegistry.delete("foo", ApplicationType.source);
			fail();
		}
		catch (NoSuchAppRegistrationException expected) {
			assertThat(expected.getMessage(), is("The 'source:foo' application could not be found."));
		}
	}

	@Test
	public void testGetAppResource() {
		AppRegistration appRegistration1 = new AppRegistration("foo", ApplicationType.source, URI.create("docker:/foo-source"));
		appRegistry.save(appRegistration1);
		assertThat(appRegistry.getAppMetadataResource(appRegistration1), nullValue());
		assertThat(appRegistry.getAppResource(appRegistration1), notNullValue());
		AppRegistration appRegistration2 = new AppRegistration("bar", ApplicationType.source, URI.create("classpath:/foo-source"));
		appRegistry.save(appRegistration2);
		assertThat(appRegistry.getAppResource(appRegistration2), notNullValue());
		assertThat(appRegistry.getAppMetadataResource(appRegistration2), notNullValue());
		AppRegistration appRegistration3 = new AppRegistration("bar", ApplicationType.source, URI.create("maven:/org.springframework.cloud:foo-source:1.0.0"));
		appRegistry.save(appRegistration3);
		assertThat(appRegistry.getAppResource(appRegistration3), notNullValue());
		assertThat(appRegistry.getAppMetadataResource(appRegistration3), notNullValue());
	}
}
