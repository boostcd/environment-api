package com.estafet.boostcd.environment.api.controller;

import com.estafet.boostcd.environment.api.service.EnvironmentService;
import com.estafet.openshift.boost.messages.environments.Environment;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EnvironmentController {

	@Autowired
	private EnvironmentService environmentService;

	@PostMapping("/environment/{product}/{env}/{action}")
	public ResponseEntity<Environment> doAction(@PathVariable String product, @PathVariable String env,
			@PathVariable String action) {
		return new ResponseEntity<Environment>(environmentService.doAction(product, env, action),
				HttpStatus.OK);
	}

}
