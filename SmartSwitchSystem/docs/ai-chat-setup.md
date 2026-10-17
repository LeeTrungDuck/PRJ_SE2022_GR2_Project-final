# AI chat configuration

The chat uses Google's Gemini `generateContent` API for questions that do not
match a built-in Smart Switch query or help answer. Keep the API key outside
source control and configure it for the Tomcat process:

- Required: `SMARTSWITCH_GEMINI_API_KEY`
- Optional model override: `SMARTSWITCH_GEMINI_MODEL`
- Default model: `gemini-2.5-flash-lite`

The equivalent JVM properties are `smartswitch.gemini.api.key` and
`smartswitch.gemini.model`. The system property takes precedence over the
environment variable. Restart Tomcat after changing process environment
variables.

For local PowerShell testing, set the key before starting Tomcat:

```powershell
$env:SMARTSWITCH_GEMINI_API_KEY = "your-new-api-key"
```

Never commit a real key, place it in a JSP/JavaScript file, or include it in
logs. If a key has previously been committed or shared, revoke it and issue a
replacement before configuring this application.
