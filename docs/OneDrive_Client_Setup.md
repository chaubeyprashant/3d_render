# OneDrive Integration – Client Setup Guide

This guide explains what the client needs to set up in **Microsoft Azure (Microsoft Entra ID)** so the RPAV app can let users browse OneDrive and open `.glb` files.

The client only needs to **register an app and send us one value (the Application / Client ID)**. No code, manifest, or Gradle changes are required from the client.

---

## What we need back from the client

| # | Item | Example |
|---|------|---------|
| 1 | **Application (client) ID** | `12345678-abcd-1234-abcd-1234567890ab` |

> No client secret is needed. The mobile app is a "public client" and uses PKCE.

---

## Step 1 – Register the application

1. Sign in to the Azure portal: <https://portal.azure.com> (use the client's Microsoft/work account).
2. Search for **Microsoft Entra ID** (formerly Azure Active Directory) and open it.
3. In the left menu choose **App registrations** → **New registration**.
4. Fill in the form:
   - **Name:** `RPAV OneDrive` (any name is fine)
   - **Supported account types:** *Accounts in any organizational directory (Any Microsoft Entra ID tenant – Multitenant) **and personal Microsoft accounts** (e.g. Skype, Xbox)*
   - **Redirect URI:** leave blank for now (added in Step 2)
5. Click **Register**.

## Step 2 – Add the Redirect URI (Android / mobile)

1. In the new app, open **Authentication** (left menu).
2. Click **Add a platform** → **Mobile and desktop applications**.
3. Under **Custom redirect URIs** enter exactly (case-sensitive, no trailing slash):

   ```
   rpav-onedrive://auth
   ```

4. Click **Configure**.
5. On the same Authentication page, scroll to **Advanced settings** and set **Allow public client flows** to **Yes**. Click **Save**.

## Step 3 – Add API permissions

1. Open **API permissions** → **Add a permission** → **Microsoft Graph** → **Delegated permissions**.
2. Search for and tick:
   - `Files.Read`
   - `User.Read` (usually already added by default)
   - `offline_access`
3. Click **Add permissions**.
4. (Optional but recommended for work/school tenants) Click **Grant admin consent for <tenant>**.

## Step 4 – Copy the Client ID

1. Open the **Overview** page of the app.
2. Copy the **Application (client) ID** (a GUID).
3. Send it to us.

---

## Message template to send us

```
OneDrive setup is complete.

Application (client) ID: <PASTE GUID HERE>
Redirect URI configured: rpav-onedrive://auth
Permissions added: Files.Read, User.Read, offline_access
Public client flows: Enabled
```

---

## Checklist

- [ ] App registered (multitenant + personal accounts)
- [ ] Redirect URI `rpav-onedrive://auth` added under *Mobile and desktop applications*
- [ ] *Allow public client flows* = Yes
- [ ] Permissions `Files.Read`, `User.Read`, `offline_access` added
- [ ] Admin consent granted (if required by the tenant)
- [ ] Application (client) ID sent to developer

---

## Troubleshooting (for the client)

| Symptom | Cause / Fix |
|---------|-------------|
| "Redirect URI mismatch" error at sign-in | URI in Azure must match exactly: `rpav-onedrive://auth` |
| "Need admin approval" screen | A tenant admin must click **Grant admin consent** in API permissions |
| Personal accounts can't sign in | Supported account types must include *personal Microsoft accounts* |

---

## For the developer (internal – not for the client)

Once the Client ID is received, add it to `gradle.properties`:

```properties
ONEDRIVE_CLIENT_ID=<client id>
```

Then rebuild the app.
