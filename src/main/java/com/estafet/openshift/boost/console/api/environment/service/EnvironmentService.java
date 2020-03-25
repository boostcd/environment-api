package com.estafet.openshift.boost.console.api.environment.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.estafet.openshift.boost.commons.lib.env.ENV;
import com.estafet.openshift.boost.console.api.environment.dao.EnvDAO;
import com.estafet.openshift.boost.console.api.environment.model.Env;
import com.estafet.openshift.boost.console.api.environment.model.EnvFactory;
import com.estafet.openshift.boost.console.api.environment.openshift.OpenShiftClient;
import com.estafet.openshift.boost.messages.environments.Environment;

@Service
public class EnvironmentService {

	@Autowired
	private OpenShiftClient client;
	
	@Autowired
	private EnvDAO envDAO;
	
	@Autowired
	private EnvFactory envFactory;
	
	public Environment doAction(String env, String action) {
		if (env.equals("build")) {
			if (action.equals("build")) {
				client.executeBuildAllPipeline();
			} else if (action.equals("promote")) {
				client.executeReleaseAllPipeline();
			}
		} else {
			if (action.equals("promote")) {
				client.executePromoteAllPipeline(env);
			} else if (action.equals("test")) {
				client.executeTestPipeline(env);
			} else if (action.equals("go-live") || action.equals("back-out")) {
				client.executePromoteToLivePipeline();
			}
		}
		return envDAO.getEnv(env).getEnvironment();
	}
	
	@Transactional
	public List<Env> updateEnvs() {
		List<Env> result = new ArrayList<Env>();
		for (Env env : envFactory.getEnvs(ENV.PRODUCT + "-build")) {
			Env savedEnv = envDAO.getEnv(env.getName());
			if (savedEnv == null) {
				result.add(env);
				envDAO.createEnv(env);
			} else {
				result.add(savedEnv.update(env));
				envDAO.updateEnv(savedEnv);
			}
		}
		return result;
	}

}
