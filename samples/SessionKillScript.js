function onInitialRequest(context) {
    executeStep({
        id: '1',
        on: {
            success: function (context) {
                var sessionIDName = "sessionID"
                var sessionAttributeMap = {};
                sessionAttributeMap["sessionLimit"] = "2";
                var sessionData = getSessionData(context, sessionAttributeMap);
                var SessionDataObject = JSON.parse(sessionData).sessions;
                Log.info("Active Session Count: " + SessionDataObject.length);
                if (SessionDataObject.length > 0) {
                    var lastSession = SessionDataObject[SessionDataObject.length - 1];
                    var terminationRequestParameters = {sessionIDName: lastSession[sessionIDName]}
                    killSession(context, terminationRequestParameters);
                }


            }
        }
    });
}
