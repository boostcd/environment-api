package com.estafet.boostcd.environment.api.service;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.URL;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.estafet.boostcd.commons.git.Git;
import com.estafet.boostcd.environment.api.dao.ProductDAO;
import com.estafet.boostcd.environment.api.model.Env;
import com.estafet.boostcd.environment.api.model.Microservice;
import com.estafet.boostcd.environment.api.model.Microservices;
import com.estafet.boostcd.environment.api.model.Product;
import com.estafet.boostcd.openshift.BuildConfigParser;
import com.estafet.boostcd.openshift.OpenShiftClient;
import com.estafet.openshift.boost.messages.github.GitHubHook;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.openshift.restclient.model.IBuildConfig;

@Service
public class GitHubService {

	@Autowired
	private OpenShiftClient client;

	@Autowired
	private ProductDAO productDAO;

	public String webhook(GitHubHook hook) {
		if (hook.getHook() != null) {
			return "ping_success";
		} else {
			for (Product product : productDAO.getProducts()) {
				for (IBuildConfig buildConfig : client.getBuildConfigs(product.getProductId())) {
					if (compareURL(hook, buildConfig)) {
						client.executeBuildPipeline(product.getProductId(), product.getRepo(), buildConfig.getName());
						return "build_success";
					}
				}
			}
			for (Product product : productDAO.getProducts()) {
				for (Env env : product.getEnvs()) {
					if (!env.getName().equals("build")) {

						IBuildConfig buildConfig = client.getTestBuildConfig(product.getProductId(), env.getName());
						if (compareURL(hook, buildConfig)) {
							client.executeTestPipeline(product.getProductId(), product.getRepo(), env.getName());
							return "test_success";
						}
					}
				}

			}
			String app = getNewApp(hook);
			if (app != null) {
				for (Product product : productDAO.getProducts()) {
					client.executeBuildPipeline(product.getProductId(), product.getRepo(), app,
							hook.getRepository().getHtmlUrl());
					return "build_success";
				}

			}
			return "no_pipline_triggered";
		}
	}

	private String getNewApp(GitHubHook hook) {
		Git git = new Git(System.getenv("PRODUCT_REPO"));
		String url = "https://raw.githubusercontent.com/" + git.uri() + "/" + git.org()
				+ "/master/src/boost/openshift/definitions/microservices.yml";
		BufferedInputStream in = null;
		try {
			in = new BufferedInputStream(new URL(url).openStream());
			ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
			Microservices microservices = mapper.readValue(in, Microservices.class);
			for (Microservice microservice : microservices.getMicroservices()) {
				if (microservice.getRepo().equals(hook.getRepository().getName())) {
					return microservice.getName();
				}
			}
			return null;
		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
		}
	}

	private boolean compareURL(GitHubHook hook, IBuildConfig buildConfig) {
		return hook.getRef().equals("refs/heads/master") && new BuildConfigParser(buildConfig).getGitRepository()
				.equalsIgnoreCase(hook.getRepository().getSvnUrl());
	}

}
