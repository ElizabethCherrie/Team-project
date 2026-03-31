# IPOS-PU to IPOS-SA Integration Guide

This note defines the recommended integration approach between Team C's `IPOS-PU` subsystem and Team 28's `IPOS-SA` subsystem.

## Integration Principle

`IPOS-PU` and `IPOS-SA` should communicate through HTTP/API calls.

They should **not** share SQLite database files directly.

Reasons:
- each subsystem owns its own internal data model
- database coupling would make the systems brittle and hard to demo reliably
- the subsystem contract should be through interfaces/endpoints, not internal tables

## Current IPOS-SA Base URL

For local development and demo:

`http://localhost:8080`

API base:

`http://localhost:8080/api`

## Current Relevant IPOS-SA Endpoints

### Non-commercial applications

Create application:

`POST /api/non-commercial-applications`

Example request body:

```json
{
  "email": "public@example.com"
}
```

List applications:

`GET /api/non-commercial-applications`

Decision endpoint:

`POST /api/non-commercial-applications/{applicationId}/decision`

Approve example:

```json
{
  "approved": true
}
```

Reject example:

```json
{
  "approved": false
}
```

### Session handoff into IPOS-SA

If Team C needs to open a user directly into an `IPOS-SA` page after login:

1. call:

`POST /api/auth/login`

2. receive:

`sessionToken`

3. open one of:

- `http://localhost:8080/merchant.html?sessionToken=<token>`
- `http://localhost:8080/login.html?sessionToken=<token>`

Session validation endpoint:

`GET /api/auth/session`

Header:

`X-Session-Token: <token>`

## What Team C Appears To Need

From the current `IPOS-PU` source code, Team C already has a registration flow where a selected file is intended to be sent to `IPOS-SA`.

At the moment, `IPOS-SA` supports:
- application email submission
- approval / rejection flow
- generated outcome logging

At the moment, `IPOS-SA` does **not** yet support file upload for application attachments.

## Recommended Short-Term Integration

For the current demo:

1. Team C submits the applicant email to `IPOS-SA`
2. Team 28 reviews the application in the manager workflow
3. Team 28 approves or rejects it in `IPOS-SA`
4. The result is logged in the `IPOS-SA` application/email flow

This is enough to demonstrate subsystem communication without introducing risky file upload work late in the project.

## Recommended Future Extension

If Team C requires attachment support, the clean extension would be:

- add attachment metadata fields to the application request
- or add a multipart upload endpoint in `IPOS-SA`

Example future direction:

`POST /api/non-commercial-applications/upload`

But this should only be added if Team C confirms they need the file content as part of the demo.

## Summary

The safest current contract between `IPOS-PU` and `IPOS-SA` is:

- applications sent by API
- no shared database
- optional session handoff by session token
- file uploads deferred unless explicitly required
