/*
   Copyright 2017 Ericsson AB.
   For a full list of individual contributors, please see the commit history.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
package com.ericsson.ei.controller;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.expression.AccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

import com.ericsson.ei.config.HttpSessionConfig;
import com.ericsson.ei.controller.model.GetSubscriptionResponse;
import com.ericsson.ei.controller.model.Subscription;
import com.ericsson.ei.controller.model.SubscriptionResponse;
import com.ericsson.ei.exception.SubscriptionNotFoundException;
import com.ericsson.ei.services.ISubscriptionService;
import com.ericsson.ei.subscriptionhandler.SubscriptionValidator;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

@Component
@CrossOrigin
@Api(value = "subscription", description = "The Subscription API for the store and retrieve the subscriptions from the database")
public class SubscriptionControllerImpl implements SubscriptionController {

    private static final Logger LOG = LoggerFactory.getLogger(SubscriptionControllerImpl.class);

    private static final String SUBSCRIPTION_NOT_FOUND = "Subscription is not found";
    private static final String SUBSCRIPTION_ALREADY_EXISTS = "Subscription already exists";
    private static final String INVALID_USER = "Unauthorized! You must be logged in as the creator of a subscription to modify it.";

    @Value("${ldap.enabled}")
    private boolean ldapEnabled;

    @Autowired
    private ISubscriptionService subscriptionService;

    private SubscriptionValidator subscriptionValidator = new SubscriptionValidator();

    private Map<String, String> errorMap;

    @Override
    @CrossOrigin
    @ApiOperation(value = "Creates the subscriptions")
    public ResponseEntity<List<SubscriptionResponse>> createSubscription(
            @RequestBody List<Subscription> subscriptions) {
        errorMap = new HashMap<>();
        String user = (ldapEnabled) ? HttpSessionConfig.getCurrentUser() : "";

        subscriptions.forEach(subscription -> {
            String subscriptionName = subscription.getSubscriptionName();
            try {
                LOG.debug("Subscription creation has been started: " + subscriptionName);
                subscriptionValidator.validateSubscription(subscription);

                if (!subscriptionService.doSubscriptionExist(subscriptionName)) {
                    subscription.setLdapUserName(user);
                    subscription.setCreated(Instant.now().toEpochMilli());
                    subscriptionService.addSubscription(subscription);
                    LOG.debug("Subscription is created successfully: " + subscriptionName);
                } else {
                    LOG.error("Subscription to create already exists: " + subscriptionName);
                    errorMap.put(subscriptionName, SUBSCRIPTION_ALREADY_EXISTS);
                }
            } catch (Exception e) {
                LOG.error("Failed to create subscription " + subscriptionName + "\nError message: " + e.getMessage(),
                        e);
                errorMap.put(subscriptionName, e.getMessage());
            }
        });
        return getResponse();
    }

    @Override
    @CrossOrigin
    @ApiOperation(value = "Returns the subscriptions for given subscription names separated by comma")
    public ResponseEntity<GetSubscriptionResponse> getSubscriptionByNames(@PathVariable String subscriptionNames) {
        // set is used to prevent subscription names repeating
        Set<String> subscriptionNamesList = new HashSet<>(Arrays.asList(subscriptionNames.split(",")));
        List<Subscription> foundSubscriptionList = new ArrayList<>();
        List<String> notFoundSubscriptionList = new ArrayList<>();

        subscriptionNamesList.forEach(subscriptionName -> {
            try {
                LOG.debug("Subscription fetching has been started: " + subscriptionName);

                // Make sure the password is not sent outside this service.
                Subscription subscription = subscriptionService.getSubscription(subscriptionName);
                subscription.setPassword("");
                foundSubscriptionList.add(subscription);
                LOG.debug("Subscription [" + subscriptionName + "] fetched successfully.");
            } catch (SubscriptionNotFoundException e) {
                LOG.error("Subscription not found: " + subscriptionName);
                notFoundSubscriptionList.add(subscriptionName);
            } catch (Exception e) {
                LOG.error("Failed to fetch subscription " + subscriptionName + "\nError message: " + e.getMessage(), e);
                notFoundSubscriptionList.add(subscriptionName);
            }
        });
        GetSubscriptionResponse response = new GetSubscriptionResponse();
        response.setFoundSubscriptions(foundSubscriptionList);
        response.setNotFoundSubscriptions(notFoundSubscriptionList);
        HttpStatus httpStatus = (!foundSubscriptionList.isEmpty()) ? HttpStatus.OK : HttpStatus.NOT_FOUND;
        return new ResponseEntity<>(response, httpStatus);
    }

    @Override
    @CrossOrigin
    @ApiOperation(value = "Updates the existing subscriptions")
    public ResponseEntity<List<SubscriptionResponse>> updateSubscriptions(
            @RequestBody List<Subscription> subscriptions) {
        errorMap = new HashMap<>();
        String user = (ldapEnabled) ? HttpSessionConfig.getCurrentUser() : "";

        subscriptions.forEach(subscription -> {
            String subscriptionName = subscription.getSubscriptionName();
            LOG.debug("Subscription updating has been started: " + subscriptionName);
            try {
                subscriptionValidator.validateSubscription(subscription);

                if (subscriptionService.doSubscriptionExist(subscriptionName)) {
                    subscription.setLdapUserName(user);
                    subscription.setCreated((float) Instant.now().toEpochMilli());
                    subscriptionService.modifySubscription(subscription, subscriptionName);
                    LOG.debug("Updating subscription completed: " + subscriptionName);
                } else {
                    LOG.error("Subscription to update was not found: " + subscriptionName);
                    errorMap.put(subscriptionName, SUBSCRIPTION_NOT_FOUND);
                }
            } catch (Exception e) {
                LOG.error("Failed to update subscription " + subscriptionName + "\nError message: " + e.getMessage(),
                        e);
                errorMap.put(subscriptionName, e.getMessage());
            }
        });
        return getResponse();
    }

    @Override
    @CrossOrigin
    @ApiOperation(value = "Removes the subscriptions from the database")
    public ResponseEntity<List<SubscriptionResponse>> deleteSubscriptionByNames(
            @PathVariable String subscriptionNames) {
        errorMap = new HashMap<>();
        // set is used to prevent subscription names repeating
        Set<String> subscriptionNamesList = new HashSet<>(Arrays.asList(subscriptionNames.split(",")));

        subscriptionNamesList.forEach(subscriptionName -> {
            LOG.debug("Subscription deleting has been started: " + subscriptionName);

            try {
                if (subscriptionService.deleteSubscription(subscriptionName)) {
                    LOG.debug("Subscription was deleted successfully: " + subscriptionName);
                } else {
                    LOG.error("Subscription to delete was not found: " + subscriptionName);
                    errorMap.put(subscriptionName, SUBSCRIPTION_NOT_FOUND);
                }
            } catch (AccessException e) {
                LOG.error("Error: " + e.getMessage());
                errorMap.put(subscriptionName, INVALID_USER);
            }
        });
        return getResponse();
    }

    @Override
    @CrossOrigin
    @ApiOperation(value = "Retrieves all the subscriptions")
    public ResponseEntity<?> getSubscriptions() {
        LOG.debug("Fetching subscriptions has been initiated");
        try {
            // Make sure the password is not sent outside this service.
            List<Subscription> subscriptions = subscriptionService.getSubscriptions();
            for (Subscription subscription : subscriptions) {
                subscription.setPassword("");
            }

            return new ResponseEntity<>(subscriptions, HttpStatus.OK);
        } catch (SubscriptionNotFoundException e) {
            LOG.info(e.getMessage());
            return new ResponseEntity<>(new ArrayList<>(), HttpStatus.OK);
        } catch (Exception e) {
            String errorMessage = "Failed to fetch subscriptions. Error message:\n" + e.getMessage();
            LOG.error(errorMessage, e);
            return new ResponseEntity<>(errorMessage, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private ResponseEntity<List<SubscriptionResponse>> getResponse() {
        if (!errorMap.isEmpty()) {
            List<SubscriptionResponse> subscriptionResponseList = new ArrayList<>();
            errorMap.forEach((subscriptionName, reason) -> {
                SubscriptionResponse subscriptionResponse = new SubscriptionResponse();
                subscriptionResponse.setSubscription(subscriptionName);
                subscriptionResponse.setReason(reason);
                subscriptionResponseList.add(subscriptionResponse);
            });
            return new ResponseEntity<>(subscriptionResponseList, HttpStatus.BAD_REQUEST);
        }
        return new ResponseEntity<>(HttpStatus.OK);
    }
}
