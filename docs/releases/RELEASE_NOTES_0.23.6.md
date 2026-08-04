# Xylune 0.23.6

- Repair setup navigation animations by retiring inactive wizard pages instead of keeping five overlapping scroll trees alive, and animate the footer controls and progress indicator consistently with step direction.
- Replace the ambiguous Later action with Skip for now while preserving the exact unfinished step.
- Add a Finish setup entry in Settings so deferred onboarding can be resumed at any time.
- After a full settings restore, enter Xylune immediately and defer only the credential/provider step because portable backups intentionally exclude credentials and OAuth sessions.
