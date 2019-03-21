/*
 * Copyright 2016-2018 the original author or authors.
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

package org.springframework.cloud.dataflow.server.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.cloud.dataflow.core.TaskDefinition;
import org.springframework.cloud.dataflow.rest.resource.TaskDefinitionResource;
import org.springframework.cloud.dataflow.rest.resource.TaskExecutionResource;
import org.springframework.cloud.dataflow.server.controller.support.TaskExecutionAwareTaskDefinition;
import org.springframework.cloud.dataflow.server.repository.NoSuchTaskDefinitionException;
import org.springframework.cloud.dataflow.server.repository.TaskDefinitionRepository;
import org.springframework.cloud.dataflow.server.repository.support.SearchPageable;
import org.springframework.cloud.dataflow.server.service.TaskService;
import org.springframework.cloud.deployer.spi.task.TaskLauncher;
import org.springframework.cloud.task.repository.TaskExecution;
import org.springframework.cloud.task.repository.TaskExplorer;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.ExposesResourceFor;
import org.springframework.hateoas.PagedResources;
import org.springframework.hateoas.mvc.ResourceAssemblerSupport;
import org.springframework.http.HttpStatus;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for operations on {@link TaskDefinition}. This includes CRUD operations.
 *
 * @author Michael Minella
 * @author Marius Bogoevici
 * @author Glenn Renfro
 * @author Mark Fisher
 * @author Gunnar Hillert
 */
@RestController
@RequestMapping("/tasks/definitions")
@ExposesResourceFor(TaskDefinitionResource.class)
public class TaskDefinitionController {

	private final Assembler taskAssembler = new Assembler();

	private TaskDefinitionRepository repository;

	private TaskService taskService;

	private final TaskExplorer explorer;

	/**
	 * Creates a {@code TaskDefinitionController} that delegates
	 * <ul>
	 * <li>CRUD operations to the provided {@link TaskDefinitionRepository}</li>
	 * <li>task status checks to the provided {@link TaskLauncher}</li>
	 * </ul>
	 *
	 * @param taskExplorer used to look up TaskExecutions
	 * @param repository the repository this controller will use for task CRUD operations.
	 * @param taskService handles specialized behavior needed for tasks.
	 */
	public TaskDefinitionController(TaskExplorer taskExplorer, TaskDefinitionRepository repository, TaskService taskService) {
		Assert.notNull(taskExplorer, "taskExplorer must not be null");
		Assert.notNull(repository, "repository must not be null");
		Assert.notNull(taskService, "taskService must not be null");
		this.explorer = taskExplorer;
		this.repository = repository;
		this.taskService = taskService;
	}

	/**
	 * Register a task definition for future execution.
	 *
	 * @param name name the name of the task
	 * @param dsl DSL definition for the task
	 * @return the task definition
	 */
	@RequestMapping(value = "", method = RequestMethod.POST)
	public TaskDefinitionResource save(@RequestParam("name") String name, @RequestParam("definition") String dsl) {
		TaskDefinition taskDefinition = new TaskDefinition(name, dsl);
		taskService.saveTaskDefinition(name, dsl);
		return taskAssembler.toResource(new TaskExecutionAwareTaskDefinition(taskDefinition));
	}

	/**
	 * Delete the task from the repository so that it can no longer be executed.
	 *
	 * @param name name of the task to be deleted
	 */
	@RequestMapping(value = "/{name}", method = RequestMethod.DELETE)
	@ResponseStatus(HttpStatus.OK)
	public void destroyTask(@PathVariable("name") String name) {
		taskService.deleteTaskDefinition(name);
	}

	/**
	 * Return a page-able list of {@link TaskDefinitionResource} defined tasks.
	 *
	 * @param pageable page-able collection of {@code TaskDefinitionResource}.
	 * @param assembler assembler for the {@link TaskDefinition}
	 * @param search optional findByNameLike parameter
	 * @return a list of task definitions
	 */
	@RequestMapping(value = "", method = RequestMethod.GET)
	@ResponseStatus(HttpStatus.OK)
	public PagedResources<TaskDefinitionResource> list(Pageable pageable, @RequestParam(required = false) String search,
			PagedResourcesAssembler<TaskExecutionAwareTaskDefinition> assembler) {

		final Page<TaskDefinition> taskDefinitions;
		if (search != null) {
			final SearchPageable searchPageable = new SearchPageable(pageable, search);
			searchPageable.addColumns("DEFINITION_NAME", "DEFINITION");
			taskDefinitions = repository.findByNameLike(searchPageable);
		}
		else {
			taskDefinitions = repository.findAll(pageable);
		}

		final java.util.HashMap<String, TaskDefinition> taskDefinitionMap = new java.util.HashMap<>();

		for (TaskDefinition taskDefinition : taskDefinitions) {
			taskDefinitionMap.put(taskDefinition.getName(), taskDefinition);
		}

		final List<TaskExecution> taskExecutions;

		if (!taskDefinitionMap.isEmpty()) {
			taskExecutions =
					this.explorer.getLatestTaskExecutionsByTaskNames(taskDefinitionMap.keySet().toArray(new String[taskDefinitionMap.size()]));
		}
		else {
			taskExecutions = null;
		}

		final Page<TaskExecutionAwareTaskDefinition> taskExecutionAwareTaskDefinitions = taskDefinitions.map(new TaskDefinitionConverter(taskExecutions));

		return assembler.toResource(taskExecutionAwareTaskDefinitions, taskAssembler);
	}

	/**
	 * Return a given task definition resource.
	 *
	 * @param name the name of an existing task definition (required)
	 * @return the task definition
	 */
	@RequestMapping(value = "/{name}", method = RequestMethod.GET)
	@ResponseStatus(HttpStatus.OK)
	public TaskDefinitionResource display(@PathVariable("name") String name) {
		TaskDefinition definition = repository.findOne(name);
		if (definition == null) {
			throw new NoSuchTaskDefinitionException(name);
		}
		final TaskExecution taskExecution = this.explorer.getLatestTaskExecutionForTaskName(name);

		if (taskExecution != null) {
			return taskAssembler.toResource(new TaskExecutionAwareTaskDefinition(definition, taskExecution));
		}
		else {
			return taskAssembler.toResource(new TaskExecutionAwareTaskDefinition(definition));
		}
	}

	/**
	 * {@link org.springframework.hateoas.ResourceAssembler} implementation that converts
	 * {@link TaskDefinition}s to {@link TaskDefinitionResource}s.
	 */
	class Assembler extends ResourceAssemblerSupport<TaskExecutionAwareTaskDefinition, TaskDefinitionResource> {

		public Assembler() {
			super(TaskDefinitionController.class, TaskDefinitionResource.class);
		}

		@Override
		public TaskDefinitionResource toResource(TaskExecutionAwareTaskDefinition taskExecutionAwareTaskDefinition) {
			return createResourceWithId(taskExecutionAwareTaskDefinition.getTaskDefinition().getName(), taskExecutionAwareTaskDefinition);
		}

		@Override
		public TaskDefinitionResource instantiateResource(TaskExecutionAwareTaskDefinition taskExecutionAwareTaskDefinition) {
			boolean composed = taskService.isComposedDefinition(taskExecutionAwareTaskDefinition.getTaskDefinition().getDslText());
			TaskDefinitionResource taskDefinitionResource = new TaskDefinitionResource(taskExecutionAwareTaskDefinition.getTaskDefinition().getName(),
					taskExecutionAwareTaskDefinition.getTaskDefinition().getDslText());
			if(taskExecutionAwareTaskDefinition.getLatestTaskExecution() != null) {
				taskDefinitionResource.setLastTaskExecution(new TaskExecutionResource(taskExecutionAwareTaskDefinition.getLatestTaskExecution()));
			}
			taskDefinitionResource.setComposed(composed);
			return taskDefinitionResource;
		}
	}

	class TaskDefinitionConverter implements Converter<TaskDefinition, TaskExecutionAwareTaskDefinition> {
		final Map<String, TaskExecution> taskExecutions;

		public TaskDefinitionConverter(List<TaskExecution> taskExecutions) {
			super();
			if (taskExecutions != null) {
				this.taskExecutions = new HashMap<>(taskExecutions.size());
				for (TaskExecution taskExecution : taskExecutions) {
					this.taskExecutions.put(taskExecution.getTaskName(), taskExecution);
				}
			}
			else {
				this.taskExecutions = null;
			}
		}

		@Override
		public TaskExecutionAwareTaskDefinition convert(TaskDefinition source) {

			TaskExecution lastTaskExecution = null;

			if (taskExecutions != null) {
				lastTaskExecution = taskExecutions.get(source.getName());
			}

			if (lastTaskExecution != null) {
				return new TaskExecutionAwareTaskDefinition(source, lastTaskExecution);
			}
			else {
				return new TaskExecutionAwareTaskDefinition(source);
			}
		}
	};
}
