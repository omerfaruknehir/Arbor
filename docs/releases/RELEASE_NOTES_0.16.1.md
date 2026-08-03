# Xylune 0.16.1

Hotfix for the upgrade-time Room schema validation crash. The 12→13 migration now declares `systemPromptProfileId` with explicit `DEFAULT NULL`, matching Room schema 13 and preserving existing encrypted chat data.
