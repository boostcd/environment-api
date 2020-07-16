package com.estafet.boostcd.environment.api.openshift;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import com.estafet.boostcd.commons.env.ENV;
import com.estafet.boostcd.environment.api.dao.EnvDAO;
import com.openshift.restclient.ClientBuilder;
import com.openshift.restclient.IClient;
import com.openshift.restclient.ResourceKind;
import com.openshift.restclient.capability.CapabilityVisitor;
import com.openshift.restclient.capability.resources.IBuildTriggerable;
import com.openshift.restclient.model.IBuild;
import com.openshift.restclient.model.IBuildConfig;
import com.openshift.restclient.model.IDeploymentConfig;
import com.openshift.restclient.model.IImageStream;
import com.openshift.restclient.model.IProject;
import com.openshift.restclient.model.IService;
import com.openshift.restclient.model.route.IRoute;

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;

@Component
public class OpenShiftClient {

	private static final Logger log = LoggerFactory.getLogger(OpenShiftClient.class);
	
	@Autowired
	private Tracer tracer;
	
	@Autowired
	private EnvDAO envDAO;

	@Cacheable(cacheNames = { "token" })
	private IClient getClient() {
		return new ClientBuilder("https://" + ENV.OPENSHIFT_HOST_PORT)
				.withUserName(ENV.OPENSHIFT_USER)
				.withPassword(ENV.OPENSHIFT_PASSWORD)
				.build();
	}
	
	@Cacheable(cacheNames = { "build" })
	@SuppressWarnings("deprecation")
	public IBuildConfig getBuildConfig(String app) {
		Span span = tracer.buildSpan("OpenShiftClient.getBuild").start();
		try {
			span.setBaggageItem("app", app);
			return (IBuildConfig) getClient().get(ResourceKind.BUILD_CONFIG, app, ENV.PRODUCT + "-build");
		} catch (RuntimeException e) {
			throw handleException(span, e);
		} finally {
			span.finish();
		}
	}

	@SuppressWarnings("deprecation")
	public List<IBuildConfig> getBuildConfigs() {
		Span span = tracer.buildSpan("OpenShiftClient.getBuildConfigs").start();
		try {
			Map<String, String> labels = new HashMap<String, String>();
			labels.put("product", ENV.PRODUCT);
			return getClient().list(ResourceKind.BUILD_CONFIG, ENV.PRODUCT + "-build", labels);
		} catch (RuntimeException e) {
			throw handleException(span, e);
		} finally {
			span.finish();
		}
	}
	
	public String repoUrl(String app) {
		return new BuildConfigParser(getBuildConfig(app)).getGitRepository();
	}
	
	@SuppressWarnings("deprecation")
	public Map<String, IProject> getProjects() {
		Span span = tracer.buildSpan("getProjects").start();
		try {
			Map<String, String> labels = new HashMap<String, String>();
			labels.put("product", ENV.PRODUCT);
			labels.put("stage", "true");
			List<IProject> projects = getClient().list(ResourceKind.PROJECT, labels);
			Map<String, IProject> result = new HashMap<String, IProject>();
			for (IProject project : projects) {
				log.debug("project - " + project.getName());
				result.put(project.getName(), project);
			}
			return result;
		} catch (RuntimeException e) {
			throw handleException(span, e);
		} finally {
			span.finish();
		}
	}

	@SuppressWarnings("deprecation")
	public boolean isEnvironmentTestPassed(IProject project) {
		Span span = tracer.buildSpan("isEnvironmentTestPassed").start();
		try {			
			span.setBaggageItem("namespace", project.getName());
			Map<String, String> labels = project.getLabels();
			String testPassed = labels.get("test-passed");
			log.debug(labels.toString());
			log.debug("testPassed - " + project.getName() + " - " + testPassed);
			return Boolean.parseBoolean(testPassed);
		} catch (RuntimeException e) {
			throw handleException(span, e);
		} finally {
			span.finish();
		}
	}
	
	@SuppressWarnings("deprecation")
	public Map<String, IDeploymentConfig> getDeploymentConfigs(String namespace) {
		Span span = tracer.buildSpan("getDeploymentConfigs").start();
		try {
			Map<String, String> labels = new HashMap<String, String>();
			labels.put("product", ENV.PRODUCT);
			span.setBaggageItem("namespace", namespace);
			List<IDeploymentConfig> dcs = getClient().list(ResourceKind.DEPLOYMENT_CONFIG, namespace, labels);
			Map<String, IDeploymentConfig> result = new HashMap<String, IDeploymentConfig>();
			for (IDeploymentConfig dc : dcs) {
				result.put(dc.getName(), dc);
			}
			return result;
		} catch (RuntimeException e) {
			throw handleException(span, e);
		} finally {
			span.finish();
		}
	}

	@SuppressWarnings("deprecation")
	public Map<String, IService> getServices(String namespace) {
		Span span = tracer.buildSpan("getServices").start();
		try {
			Map<String, String> labels = new HashMap<String, String>();
			labels.put("product", ENV.PRODUCT);
			span.setBaggageItem("namespace", namespace);
			List<IService> services = getClient().list(ResourceKind.SERVICE, namespace, labels);
			Map<String, IService> result = new HashMap<String, IService>();
			for (IService service : services) {
				result.put(service.getName(), service);
			}
			return result;
		} catch (RuntimeException e) {
			throw handleException(span, e);
		} finally {
			span.finish();
		}
	}
	
	@SuppressWarnings("deprecation")
	public Map<String, IImageStream> getImageStreams(String namespace) {
		Span span = tracer.buildSpan("getImageStreams").start();
		try {
			Map<String, String> labels = new HashMap<String, String>();
			labels.put("product", ENV.PRODUCT);
			List<IImageStream> images = getClient().list(ResourceKind.IMAGE_STREAM, namespace, labels);
			Map<String, IImageStream> result = new HashMap<String, IImageStream>();
			for (IImageStream image : images) {
				result.put(image.getName(), image);
			}
			return result;
		} catch (RuntimeException e) {
			throw handleException(span, e);
		} finally {
			span.finish();
		}
	}
	
	@SuppressWarnings("deprecation")
	public Map<String, IImageStream> getCICDImageStreams() {
		Span span = tracer.buildSpan("getCICDImageStreams").start();
		try {
			List<IImageStream> images = getClient().list(ResourceKind.IMAGE_STREAM, ENV.CICD);
			Map<String, IImageStream> result = new HashMap<String, IImageStream>();
			for (IImageStream image : images) {
				result.put(image.getName(), image);
			}
			return result;
		} catch (RuntimeException e) {
			throw handleException(span, e);
		} finally {
			span.finish();
		}
	}
	
	@SuppressWarnings("deprecation")
	public IRoute getRoute() {
		Span span = tracer.buildSpan("getRoute").start();
		try {
			Map<String, String> labels = new HashMap<String, String>();
			labels.put("product", ENV.PRODUCT);
			return (IRoute) getClient().list(ResourceKind.ROUTE, ENV.PRODUCT + "-prod", labels).get(0);
		} catch (RuntimeException e) {
			throw handleException(span, e);
		} finally {
			span.finish();
		}
	}

	@SuppressWarnings("deprecation")
	public void executeBuildPipeline(String app, String repoUrl) {
		Span span = tracer.buildSpan("executeBuildPipeline").start();
		try {
			span.setBaggageItem("app",app);
			Map<String, String> parameters = getAppParameters(app, repoUrl);
			executePipeline((IBuildConfig) getClient().get(ResourceKind.BUILD_CONFIG, "build-" + app, ENV.CICD), parameters);
		} catch (RuntimeException e) {
			throw handleException(span, e);
		} finally {
			span.finish();
		}
	}
	
	private Map<String, String> getAppParameters(String app, String repoUrl) {
		Map<String, String> parameters = new HashMap<String, String>();
		parameters.put("REPO", repoUrl);
		parameters.put("PRODUCT", ENV.PRODUCT);
		parameters.put("MICROSERVICE", app);
		parameters.put("PRODUCT_REPO", System.getenv("PRODUCT_REPO"));
		return parameters;
	}
	
	@SuppressWarnings("deprecation")
	public void executeBuildPipeline(String app) {
		Span span = tracer.buildSpan("executeBuildPipeline").start();
		try {
			span.setBaggageItem("app",app);
			Map<String, String> parameters = getAppParameters(app);
			executePipeline((IBuildConfig) getClient().get(ResourceKind.BUILD_CONFIG, "build-" + app, ENV.CICD), parameters);
		} catch (RuntimeException e) {
			throw handleException(span, e);
		} finally {
			span.finish();
		}
	}

	private Map<String, String> getAppParameters(String app) {
		Map<String, String> parameters = new HashMap<String, String>();
		parameters.put("REPO", repoUrl(app));
		parameters.put("PRODUCT", ENV.PRODUCT);
		parameters.put("MICROSERVICE", app);
		parameters.put("PRODUCT_REPO", System.getenv("PRODUCT_REPO"));
		return parameters;
	}
	
	@SuppressWarnings("deprecation")
	public void executeBuildAllPipeline() {
		Span span = tracer.buildSpan("executeBuildAllPipeline").start();
		try {
			Map<String, String> parameters = new HashMap<String, String>();
			parameters.put("REPO", System.getenv("PRODUCT_REPO"));
			parameters.put("PRODUCT", ENV.PRODUCT);
			executePipeline((IBuildConfig) getClient().get(ResourceKind.BUILD_CONFIG, "build-all", ENV.CICD), parameters);
		} catch (RuntimeException e) {
			throw handleException(span, e);
		} finally {
			span.finish();
		}
	}
	
	@SuppressWarnings("deprecation")
	public void executeReleasePipeline(String app) {
		Span span = tracer.buildSpan("executeReleasePipeline").start();
		try {
			span.setBaggageItem("app",app);
			Map<String, String> parameters = getAppParameters(app);
			executePipeline((IBuildConfig) getClient().get(ResourceKind.BUILD_CONFIG, "release-" + app, ENV.CICD), parameters);
		} catch (RuntimeException e) {
			throw handleException(span, e);
		} finally {
			span.finish();
		}
	}
	
	@SuppressWarnings("deprecation")
	public void executeReleaseAllPipeline() {
		Span span = tracer.buildSpan("executeReleaseAllPipeline").start();
		try {
			Map<String, String> parameters = new HashMap<String, String>();
			parameters.put("REPO", System.getenv("PRODUCT_REPO"));
			parameters.put("PRODUCT", ENV.PRODUCT);
			executePipeline((IBuildConfig) getClient().get(ResourceKind.BUILD_CONFIG, "release-all", ENV.CICD), parameters);
		} catch (RuntimeException e) {
			throw handleException(span, e);
		} finally {
			span.finish();
		}
	}
	
	@SuppressWarnings("deprecation")
	public void executePromotePipeline(String env, String app) {
		Span span = tracer.buildSpan("executePromotePipeline").start();
		try {
			span.setBaggageItem("env", env);
			span.setBaggageItem("app", app);
			Map<String, String> parameters = getAppParameters(app);
			parameters.put("PROJECT", ENV.namespace(env));
			String pipeline;
			if (envDAO.getEnv(env).getNext().equals("prod")) {
				pipeline = "promote-to-prod-" + app;
			} else {
				pipeline = "promote-" + env + "-" + app;
			}
			executePipeline(getClient().get(ResourceKind.BUILD_CONFIG, pipeline, ENV.CICD), parameters);
		} catch (RuntimeException e) {
			throw handleException(span, e);
		} finally {
			span.finish();
		}
	}

	@SuppressWarnings("deprecation")
	public void executePromoteAllPipeline(String env) {
		Span span = tracer.buildSpan("executePromoteAllPipeline").start();
		try {
			Map<String, String> parameters = getEnvParameters(env);
			span.setBaggageItem("env", env);
			String name;
			if (envDAO.getEnv(env).getNext().equals("prod")) {
				name = "promote-all-to-prod";
			} else {
				name = "promote-all-" + env;
			}
			IBuildConfig pipeline = getClient().get(ResourceKind.BUILD_CONFIG, name, ENV.CICD);
			executePipeline(pipeline, parameters);
		} catch (RuntimeException e) {
			throw handleException(span, e);
		} finally {
			span.finish();
		}
	}

	private Map<String, String> getEnvParameters(String env) {
		Map<String, String> parameters = new HashMap<String, String>();
		parameters.put("REPO", System.getenv("PRODUCT_REPO"));
		parameters.put("PRODUCT", ENV.PRODUCT);
		parameters.put("PROJECT", ENV.namespace(env));
		return parameters;
	}
	
	@SuppressWarnings("deprecation")
	public void executeTestPipeline(String env) {
		Span span = tracer.buildSpan("executeTestPipeline").start();
		try {
			IBuildConfig testPipeline = getTestBuildConfig(env);
			Map<String, String> parameters = getEnvParameters(env);
			String gitRepository = new BuildConfigParser(testPipeline).getGitRepository();
			parameters.put("REPO",  gitRepository);
			span.setBaggageItem("env", env);
			executePipeline(getTestWrapperBuildConfig(env), parameters);
		} catch (RuntimeException e) {
			throw handleException(span, e);
		} finally {
			span.finish();
		}
	}

	@Cacheable(cacheNames = { "test" })
	public IBuildConfig getTestWrapperBuildConfig(String env) {
		String pipeline = env.equals("blue") || env.equals("green") ? "qa-prod" : "qa-" + env;
		IBuildConfig testPipeline = (IBuildConfig) getClient().get(ResourceKind.BUILD_CONFIG, pipeline, ENV.CICD);
		return testPipeline;
	}

	@Cacheable(cacheNames = { "test" })
	public IBuildConfig getTestBuildConfig(String env) {
		String pipeline = env.equals("blue") || env.equals("green") ? "qa-prod-impl" : "qa-" + env + "-impl";
		IBuildConfig testPipeline = (IBuildConfig) getClient().get(ResourceKind.BUILD_CONFIG, pipeline, ENV.CICD);
		return testPipeline;
	}
	
	@SuppressWarnings("deprecation")
	public void executePromoteToLivePipeline() {
		Span span = tracer.buildSpan("executePromoteToLivePipeline").start();
		try {
			Map<String, String> parameters = new HashMap<String, String>();
			parameters.put("PRODUCT", ENV.PRODUCT);
			executePipeline((IBuildConfig) getClient().get(ResourceKind.BUILD_CONFIG, "promote-to-live", ENV.CICD), parameters);
		} catch (RuntimeException e) {
			throw handleException(span, e);
		} finally {
			span.finish();
		}
	}
	
	private void executePipeline(IBuildConfig pipeline, Map<String, String> parameters) {
		pipeline.accept(new CapabilityVisitor<IBuildTriggerable, IBuild>() {
            @Override
            public IBuild visit(IBuildTriggerable capability) {
            	for (String parameter : parameters.keySet()) {
            		capability.setEnvironmentVariable(parameter, parameters.get(parameter));
            	}
                return capability.trigger();
            }
        }, null);
	}
	
	private RuntimeException handleException(Span span, RuntimeException e) {
		Tags.ERROR.set(span, true);
		Map<String, Object> logs = new HashMap<String, Object>();
		logs.put("event", "error");
		logs.put("error.object", e);
		logs.put("message", e.getMessage());
		StringWriter sw = new StringWriter();
		e.printStackTrace(new PrintWriter(sw));
		logs.put("stack", sw.toString());
		span.log(logs);
		return e;
	}

}