
**Active session count based authenticator**

Welcome to the WSO2 Identity Server (IS) Session Count Based Authenticator. 

## Introduction

Active session count based authenticator can be used with WSO2 Identity Server to provide authentication based on the user's current active session count. This enables administrators to enforce active session count limitations. This component requires WSO2 Identity Server Analytics to retrieve user session information. Further this can be used with conditional authentication functions ([Repository](https://github.com/wso2-extensions/identity-conditional-auth-functions)) to provide customized authentication with conditional authentication. 

## Getting started
1. Download WSO2 Identity Server and extract it.
2. Download WSO2 Identity Server Analytics and  extract it.
3. Build Active session count based authenticator (using `mvn clean install`)
4. Go to **components/org.wso2.carbon.identity.application.authenticator.sessionauth/target**
5. Copy **org.wso2.carbon.identity.application.authenticator.sessionauth-<version>.jar** file to **<IS_HOME>/repository/components/dropings folder**.
6. Go to **components/org.wso2.carbon.identity.application.authentication-endpoint/target**.
7. Copy **sessioncountauthenticationendpoint.war** file to **<IS_HOME>/repository/deployment/server/webapps** folder
8. Configure Identity Server Analytics as described in this [document](https://docs.wso2.com/display/IS550/Prerequisites+to+Publish+Statistics).
9. Start the WSO2 Identity Server and WSO2 Identity Server Analytics and use the Active session count based authenticator.

## Sample
This sample describes how this authenticator can be used to limit user session count with the help of [Conditional Authentication Functions](https://github.com/wso2-extensions/identity-conditional-auth-functions). Use the below conditional authentication script.
<code>

function onInitialRequest(context) {
    executeStep({

        id: '1',

        on: {
            success: function (context) {
                var isAllowed = isWithinSessionLimit(context, {"sessionLimit":"2"});
                if (isAllowed) {
                    Log.info('Authentication Successfull ');
                }
                else {
                    executeStep({id: '2'});
                    Log.info('Authentication Successfull ')
                }
            }
        }
    });}
</code>

Please note that in here ID 1 is the Basic Authenticator and ID 2 is the sessionCount Authenticator
