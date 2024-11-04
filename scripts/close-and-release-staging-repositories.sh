#!/bin/bash

# Check if all required arguments are provided
if [ "$#" -ne 5 ]; then
  echo "Usage: $0 <SONATYPE_USERNAME> <SONATYPE_PASSWORD> <GROUP_ID> <ARTIFACT_ID> <DISCORD_WEBHOOK_URL>"
  exit 1
fi

# Assign arguments to variables
SONATYPE_USERNAME="$1"
SONATYPE_PASSWORD="$2"
GROUP_ID="$3"
ARTIFACT_ID="$4"
DISCORD_WEBHOOK_URL="$5"

# Find all open staging repositories for your group ID
echo "Finding open staging repositories for group: $GROUP_ID"
REPO_IDS=$(curl -s -u "$SONATYPE_USERNAME:$SONATYPE_PASSWORD" \
  "https://oss.sonatype.org/service/local/staging/profile_repositories" \
  | jq -r --arg GROUP_ID "$GROUP_ID" '.data[] | select(.type == "open" and .profileName == $GROUP_ID) | .repositoryId')

# Check if there are any open repositories to close
if [ -z "$REPO_IDS" ]; then
  echo "No open staging repositories found for group $GROUP_ID."
  exit 0
fi

for REPO_ID in $REPO_IDS; do
  echo "Checking contents of staging repository: $REPO_ID"

  # List contents of the repository
  CONTENTS=$(curl -s -u "$SONATYPE_USERNAME:$SONATYPE_PASSWORD" \
    "https://oss.sonatype.org/service/local/repositories/$REPO_ID/content")

  # Check if the artifactId is in the contents
  if echo "$CONTENTS" | grep -q "$ARTIFACT_ID"; then
    echo "Found $ARTIFACT_ID in repository $REPO_ID. Closing and releasing..."

    # Close the staging repository
    curl -s -u "$SONATYPE_USERNAME:$SONATYPE_PASSWORD" \
      -X POST "https://oss.sonatype.org/service/local/staging/profiles/$REPO_ID/finish" \
      -H "Content-Type: application/json" \
      -d "{\"data\": {\"stagedRepositoryId\": \"$REPO_ID\", \"description\": \"Automated close for $REPO_ID\"}}"

    echo "Closed staging repository: $REPO_ID"

    # Wait a few seconds for the repository to close
    sleep 10

    # Release the staging repository
    curl -s -u "$SONATYPE_USERNAME:$SONATYPE_PASSWORD" \
      -X POST "https://oss.sonatype.org/service/local/staging/profiles/$REPO_ID/promote" \
      -H "Content-Type: application/json" \
      -d "{\"data\": {\"stagedRepositoryId\": \"$REPO_ID\", \"description\": \"Automated release for $REPO_ID\"}}"

    echo "Released staging repository: $REPO_ID"

    # Send a message to Discord
    echo "Sending notification to Discord"
    curl -H "Content-Type: application/json" -X POST "$DISCORD_WEBHOOK_URL" \
      -d "{\"content\": \"Released staging repository $REPO_ID for artifact $ARTIFACT_ID in group $GROUP_ID.\"}"

    echo "Notification sent to Discord"
  else
    echo "$ARTIFACT_ID not found in repository $REPO_ID. Skipping."
  fi
done
