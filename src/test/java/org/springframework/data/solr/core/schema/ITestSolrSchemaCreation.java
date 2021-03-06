/*
 * Copyright 2014-2020 the original author or authors.
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
package org.springframework.data.solr.core.schema;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.request.schema.SchemaRequest;
import org.apache.solr.client.solrj.request.schema.SchemaRequest.AddField;
import org.apache.solr.client.solrj.response.schema.SchemaRepresentation;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.annotation.Id;
import org.springframework.data.solr.core.SolrTemplate;
import org.springframework.data.solr.core.mapping.Indexed;
import org.springframework.data.solr.core.mapping.SolrDocument;
import org.springframework.data.solr.core.query.SimpleQuery;
import org.springframework.data.solr.core.schema.SolrPersistentEntitySchemaCreator.Feature;
import org.springframework.data.solr.test.util.EmbeddedSolrServer;
import org.springframework.data.solr.test.util.EmbeddedSolrServer.ClientCache;

/**
 * @author Christoph Strobl
 */
public class ITestSolrSchemaCreation {

	public @Rule EmbeddedSolrServer resource = EmbeddedSolrServer.configure(new ClassPathResource("managed-schema"),
			ClientCache.ENABLED);

	static final String COLLECTION_NAME = "collection1";

	private SolrTemplate template;

	@Before
	public void setUp() {

		this.template = new SolrTemplate(resource);
		template.setSchemaCreationFeatures(Collections.singletonList(Feature.CREATE_MISSING_FIELDS));
		template.afterPropertiesSet();

		template.delete(COLLECTION_NAME, new SimpleQuery("*:*"));
		template.commit(COLLECTION_NAME);
	}

	@Test // DATASOLR-72, DATASOLR-313
	public void beanShouldBeSavedCorrectly() throws SolrServerException, IOException {

		Foo foo = new Foo();
		foo.id = "1";
		template.saveBean(COLLECTION_NAME, foo);

		Map<String, Map<String, Object>> fields = requestSchemaFields();

		// @Indexed String indexedStringWithoutType;
		assertThat(fields.get("indexedStringWithoutType").<String> get("type")).isEqualTo("string");
		assertThat(fields.get("indexedStringWithoutType").get("multiValued")).isEqualTo(false);
		assertThat(fields.get("indexedStringWithoutType").get("indexed")).isEqualTo(true);
		assertThat(fields.get("indexedStringWithoutType").get("stored")).isEqualTo(true);
		assertThat(fields.get("indexedStringWithoutType").get("required")).isEqualTo(false);

		// @Indexed(name = "namedField", type = "string", searchable = false)
		assertThat(fields.get("namedField").get("type")).isEqualTo("string");
		assertThat(fields.get("namedField").get("multiValued")).isEqualTo(false);
		assertThat(fields.get("namedField").get("indexed")).isEqualTo(false);
		assertThat(fields.get("namedField").get("stored")).isEqualTo(true);
		assertThat(fields.get("namedField").get("required")).isEqualTo(false);

		// @Indexed List<String> listField;
		assertThat(fields.get("listField").get("type")).isEqualTo("string");
		assertThat(fields.get("listField").get("multiValued")).isEqualTo(true);
		assertThat(fields.get("listField").get("indexed")).isEqualTo(true);
		assertThat(fields.get("listField").get("stored")).isEqualTo(true);
		assertThat(fields.get("listField").get("required")).isEqualTo(false);

		// @Indexed(type = "tdouble") Double someDoubleValue;
		assertThat(fields.get("someDoubleValue").get("type")).isEqualTo("tdouble");
		assertThat(fields.get("someDoubleValue").get("multiValued")).isEqualTo(false);
		assertThat(fields.get("someDoubleValue").get("indexed")).isEqualTo(true);
		assertThat(fields.get("someDoubleValue").get("stored")).isEqualTo(true);
		assertThat(fields.get("someDoubleValue").get("required")).isEqualTo(false);

		// @Indexed(name = "copySource", type = "string", copyTo = { "_text_" }) String aCopyFiled;
		assertThat(fields.get("copySource").get("type")).isEqualTo("string");
		assertThat(fields.get("copySource").get("multiValued")).isEqualTo(false);
		assertThat(fields.get("copySource").get("indexed")).isEqualTo(true);
		assertThat(fields.get("copySource").get("stored")).isEqualTo(true);
		assertThat(fields.get("copySource").get("required")).isEqualTo(false);

		Map<String, Object> hm = new HashMap<>();
		hm.put("source", "copySource");
		hm.put("dest", "_text_");

		assertThat(requestCopyFields()).contains(hm);
	}

	@Test // DATASOLR-313
	public void existingFieldsShouldNotBeTouched() throws SolrServerException, IOException, InterruptedException {

		Map<String, Object> field = new LinkedHashMap<>();
		field.put("name", "indexedStringWithoutType");
		field.put("type", "string");
		field.put("multiValued", true);
		field.put("indexed", false);
		field.put("stored", true);

		new AddField(field).process(resource.getSolrClient(COLLECTION_NAME), COLLECTION_NAME);

		Foo foo = new Foo();
		foo.id = "1";
		template.saveBean(COLLECTION_NAME, foo);

		Map<String, Map<String, Object>> fields = requestSchemaFields();

		// @Indexed String indexedStringWithoutType;
		assertThat(fields.get("indexedStringWithoutType").get("type")).isEqualTo("string");
		assertThat(fields.get("indexedStringWithoutType").get("multiValued")).isEqualTo(true);
		assertThat(fields.get("indexedStringWithoutType").get("indexed")).isEqualTo(false);
		assertThat(fields.get("indexedStringWithoutType").get("stored")).isEqualTo(true);
		assertThat(fields.get("indexedStringWithoutType").get("required")).isNull();

		// @Indexed(name = "namedField", type = "string", searchable = false)
		assertThat(fields.get("namedField").get("type")).isEqualTo("string");
		assertThat(fields.get("namedField").get("multiValued")).isEqualTo(false);
		assertThat(fields.get("namedField").get("indexed")).isEqualTo(false);
		assertThat(fields.get("namedField").get("stored")).isEqualTo(true);
		assertThat(fields.get("namedField").get("required")).isEqualTo(false);

		// @Indexed List<String> listField;
		assertThat(fields.get("listField").get("type")).isEqualTo("string");
		assertThat(fields.get("listField").get("multiValued")).isEqualTo(true);
		assertThat(fields.get("listField").get("indexed")).isEqualTo(true);
		assertThat(fields.get("listField").get("stored")).isEqualTo(true);
		assertThat(fields.get("listField").get("required")).isEqualTo(false);

		// @Indexed(type = "tdouble") Double someDoubleValue;
		assertThat(fields.get("someDoubleValue").get("type")).isEqualTo("tdouble");
		assertThat(fields.get("someDoubleValue").get("multiValued")).isEqualTo(false);
		assertThat(fields.get("someDoubleValue").get("indexed")).isEqualTo(true);
		assertThat(fields.get("someDoubleValue").get("stored")).isEqualTo(true);
		assertThat(fields.get("someDoubleValue").get("required")).isEqualTo(false);
	}

	private Map<String, Map<String, Object>> requestSchemaFields() throws SolrServerException, IOException {

		SchemaRepresentation schema = new SchemaRequest().process(resource.getSolrClient(COLLECTION_NAME), COLLECTION_NAME)
				.getSchemaRepresentation();

		Map<String, Map<String, Object>> fields = new LinkedHashMap<>();

		for (Map<String, Object> field : schema.getFields()) {
			fields.put(field.get("name").toString(), field);
		}

		return fields;
	}

	private List<Map<String, Object>> requestCopyFields() throws SolrServerException, IOException {

		SchemaRepresentation schema = new SchemaRequest().process(resource.getSolrClient(COLLECTION_NAME), COLLECTION_NAME)
				.getSchemaRepresentation();

		return schema.getCopyFields();
	}

	@SolrDocument(solrCoreName = COLLECTION_NAME)
	public static class Foo {

		@Indexed @Id String id;

		@Indexed String indexedStringWithoutType;

		@Indexed(name = "namedField", type = "string", searchable = false) String justAStoredField;

		@Indexed List<String> listField;

		@Indexed(type = "tdouble") Double someDoubleValue;

		@Indexed(name = "copySource", type = "string", copyTo = { "_text_" }) String aCopyFiled;
	}

}
