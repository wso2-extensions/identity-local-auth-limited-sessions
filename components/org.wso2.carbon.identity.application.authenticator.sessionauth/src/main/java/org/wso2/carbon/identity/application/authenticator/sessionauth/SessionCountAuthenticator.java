/*
 * Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.wso2.carbon.identity.application.authenticator.sessionauth;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.ssl.Base64;
import org.json.JSONArray;
import org.wso2.carbon.identity.application.authentication.framework.AbstractApplicationAuthenticator;
import org.wso2.carbon.identity.application.authentication.framework.AuthenticatorFlowStatus;
import org.wso2.carbon.identity.application.authentication.framework.LocalApplicationAuthenticator;
import org.wso2.carbon.identity.application.authentication.framework.config.model.StepConfig;
import org.wso2.carbon.identity.application.authentication.framework.context.AuthenticationContext;
import org.wso2.carbon.identity.application.authentication.framework.exception.AuthenticationFailedException;
import org.wso2.carbon.identity.application.authentication.framework.model.AuthenticatedUser;
import org.wso2.carbon.identity.application.authentication.framework.services.SessionManagementService;
import org.wso2.carbon.identity.application.authentication.framework.util.FrameworkUtils;
import org.wso2.carbon.identity.application.authenticator.sessionauth.exception.SessionValidationException;
import org.wso2.carbon.identity.application.authenticator.sessionauth.util.AuthenticatorUtil;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static java.lang.Integer.parseInt;
import static org.wso2.carbon.identity.application.authenticator.sessionauth.util.AuthenticatorUtil.getSessionDetails;

/**
 * Session count based authenticator.
 */
public class SessionCountAuthenticator extends AbstractApplicationAuthenticator
        implements LocalApplicationAuthenticator {

    private static final long serialVersionUID = 1819664539416029245L;
    private static final Log log = LogFactory.getLog(SessionCountAuthenticator.class);

    @Override
    public boolean canHandle(HttpServletRequest request) {

        return true;
    }

    @Override
    public AuthenticatorFlowStatus process(HttpServletRequest request,
                                           HttpServletResponse response, AuthenticationContext context)
            throws AuthenticationFailedException {
        if (context.isLogoutRequest()) {
            return AuthenticatorFlowStatus.SUCCESS_COMPLETED;
        }
        //checks whether request has gone through the session.jsp page or not
        if (StringUtils.isNotEmpty(request.getParameter(
                SessionCountAuthenticatorConstants.SESSION_TERMINATION_SERVLET_INPUT))) {
            try {
                processAuthenticationResponse(request, response, context);
            } catch (SessionValidationException e) {
                context.setRetrying(true);
                context.setCurrentAuthenticator(getName());
                return initiateAuthRequest(response, context,
                        "Exception occurred in session termination process. Please try again");
            }
            return AuthenticatorFlowStatus.SUCCESS_COMPLETED;
        } else {
            return initiateAuthRequest(response, context, null);
        }
    }

    /**
     * This will gather information on exiting sessions of the current user trying to execute the authenticator and
     * store them as metadata and redirect user to page to select sessions to termination.
     *
     * @param response     the response
     * @param context      the authentication context
     * @param errorMessage contains error message of previous attempt if this is a retry attempt.
     * @throws AuthenticationFailedException when a problem occurred in session termination process, user
     *                                       authentication process or meta data retrieving process
     */
    private AuthenticatorFlowStatus initiateAuthRequest(HttpServletResponse response, AuthenticationContext context,
                                                        String errorMessage) throws AuthenticationFailedException {

        AuthenticatedUser authenticatedUser = null;
        StepConfig stepConfig = null;
        //Identifying the authenticated user from the previous step
        stepConfig = context.getSequenceConfig().getStepMap().get(context.getCurrentStep() - 1);
        if (stepConfig != null) {
            authenticatedUser = stepConfig.getAuthenticatedUser();
        } else {
            throw new AuthenticationFailedException("Failed to identify the previous authentication step");
        }
        if (authenticatedUser == null) {
            throw new AuthenticationFailedException("Authentication failed!. Failed to identify the user");
        }
        if (stepConfig.getAuthenticatedAutenticator().getApplicationAuthenticator() instanceof
                LocalApplicationAuthenticator) {
            String loginPage = AuthenticatorUtil.getLoginPageURL();
            try {
                JSONArray sessionMetaData = getSessionDetails(authenticatedUser);
                int sessionLimit = getAllowedSessionLimit(context, sessionMetaData.length());
                if (log.isDebugEnabled()) {
                    log.debug("Decided session limit is :" + sessionLimit);
                }
                byte[] encodedBytes = Base64.encodeBase64(sessionMetaData.toString().getBytes(Charset.forName(
                        StandardCharsets.UTF_8.name())));
                String queryParams = FrameworkUtils.getQueryStringWithFrameworkContextId(context.getQueryParams(),
                        context.getCallerSessionKey(), context.getContextIdentifier());
                String retryParam = "";
                if (context.isRetrying()) {
                    retryParam = "&authFailure=true&authFailureMsg=" + errorMessage;
                }
                String encodedUrl = loginPage + ("?" + queryParams + "&sessionData=" + new String(encodedBytes,
                        StandardCharsets.UTF_8.name()) +
                        "&sessionLimit=" + String.valueOf(sessionMetaData.length())
                        + "&terminateCount=" + String.valueOf(sessionLimit))
                        + "&authenticators=" + getName() + ":" + SessionCountAuthenticatorConstants.AUTHENTICATOR_TYPE
                        + retryParam;
                response.sendRedirect(encodedUrl);
                context.setCurrentAuthenticator(getName());
                return AuthenticatorFlowStatus.INCOMPLETE;

            } catch (IOException e) {
                throw new AuthenticationFailedException("Problem occurred in redirecting to the session termination page", e);
            } catch (SessionValidationException e) {
                throw new AuthenticationFailedException("Failed to retrieve session metadata", e);
            }
        }
        return AuthenticatorFlowStatus.SUCCESS_COMPLETED;
    }

    /**
     * Method to retrieve the number of allowed sessions defined by the admin.
     *
     * @param context            Context object
     * @param activeSessionCount current active session Limit
     * @return integer indicating the allowed session limit
     */
    private int getAllowedSessionLimit(AuthenticationContext context, int activeSessionCount) {
        //Default value for session limit. If session limit is not defined in context properties, current session
        // count - 1 will be taken as the session limit. This can be removed or replaced with configuration value
        int sessionLimit = activeSessionCount - 1;
        Object sessionLimitObject = context.getProperty(SessionCountAuthenticatorConstants.SESSION_LIMIT_TAG);
        if (sessionLimitObject != null) {
            if (log.isDebugEnabled()) {
                log.debug("Session Limit defined in the context");
            }
            try {
                sessionLimit = parseInt(sessionLimitObject.toString());
            } catch (NumberFormatException e) {
                log.error("Invalid string value found as session Limit", e);
            }
        }
        if (sessionLimit < 0) {
            return 0;
        }
        return sessionLimit;
    }

    @Override
    protected void processAuthenticationResponse(HttpServletRequest request,
                                                 HttpServletResponse response, AuthenticationContext context)
            throws AuthenticationFailedException {
        int closedSessionCount = 0;
        int sessionLimit = parseInt(request.getParameter(SessionCountAuthenticatorConstants.SESSION_LIMIT_TAG));
        int activeSessionCount = parseInt(request.getParameter(SessionCountAuthenticatorConstants
                .ACTIVE_SESSION_COUNT_TAG));
        SessionManagementService sessionManagementService = new SessionManagementService();
        List<String> sessionIDList = getSelectedSessionIDs(request.getParameterMap());
        for (String sessionId : sessionIDList) {
            boolean isRemoved = sessionManagementService.removeSession(sessionId);
            if (isRemoved) {
                closedSessionCount++;
                if (log.isDebugEnabled()) {
                    log.debug("Session with Session ID :" + sessionId + " removed as requested.");
                }
            }
        }
        if (activeSessionCount - closedSessionCount >= sessionLimit) {

            throw new SessionValidationException("Terminated session amount is not sufficient to continue");
        }
    }

    @Override
    protected boolean retryAuthenticationEnabled() {

        return true;
    }

    @Override
    public String getContextIdentifier(HttpServletRequest request) {

        return request.getParameter(SessionCountAuthenticatorConstants.SESSION_DATA_KEY_TAG);
    }

    @Override
    public String getFriendlyName() {

        return SessionCountAuthenticatorConstants.AUTHENTICATOR_FRIENDLY_NAME;
    }

    @Override
    public String getName() {

        return SessionCountAuthenticatorConstants.AUTHENTICATOR_NAME;
    }

    /**
     * Method for retrieving selected session Ids from the parameter map.
     *
     * @param parameterMap Map object of parameters
     * @return Array List of Session ID strings
     */
    private List<String> getSelectedSessionIDs(Map<String, String[]> parameterMap) {

        Set<String> keySet = parameterMap.keySet();
        ArrayList<String> sessionIdList = new ArrayList<>();
        for (Object key : keySet) {
            sessionIdList.add(key.toString());
        }
        sessionIdList.remove(SessionCountAuthenticatorConstants.SESSION_TERMINATION_DATA_TAG);
        sessionIdList.remove(SessionCountAuthenticatorConstants.SESSION_DATA_KEY_TAG);
        sessionIdList.remove(SessionCountAuthenticatorConstants.SESSION_LIST_TAG);
        sessionIdList.remove(SessionCountAuthenticatorConstants.ACTIVE_SESSION_COUNT_TAG);
        sessionIdList.remove(SessionCountAuthenticatorConstants.SESSION_LIMIT_TAG);
        if (sessionIdList.contains(SessionCountAuthenticatorConstants.NAME_TAG)) {
            sessionIdList.remove(SessionCountAuthenticatorConstants.NAME_TAG);
        }
        return sessionIdList;
    }
}
