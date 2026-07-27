# Arbor 0.19.2

## ChatGPT usage & limits

The ChatGPT OAuth provider card now shows the quota data reported for the signed-in account:

- account plan
- primary and secondary usage windows
- percentage used and remaining
- reset time
- additional named limits such as code review when supplied
- credit balance or unlimited-credit status when supplied

The result is cached for one minute. Manual refresh is available, and Arbor keeps displaying the last successful snapshot if a refresh temporarily fails.

## Package installation progress

APT, dpkg, and apk installations no longer appear frozen. Both the Tool workspaces screen and AI-generated Linux package cards now show:

- transaction phase
- determinate percentage when the package manager exposes one
- current package
- current status message
- bounded live stdout/stderr

APT protocol status lines are stripped from the final readable log.

## Compatibility

- Version code: 112
- Application ID: `app.arbor.chat.debug`
- Existing Room schemas, app data, provider credentials, chats, workspaces, appearance settings, and debug-signing compatibility are preserved.
