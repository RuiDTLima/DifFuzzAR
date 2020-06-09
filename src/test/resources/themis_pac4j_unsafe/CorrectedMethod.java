public void validate_unsafe$Modification(UsernamePasswordCredentials credentials) throws HttpAction {
    Handle h = null;
    try {
        h = dbi.open();
        final String username = credentials.getUsername();
        final String query;
        if (CommonHelper.isNotBlank(attributes)) {
            query = ((startQuery + ", ") + attributes) + endQuery;
        } else {
            query = startQuery + endQuery;
        }
        final List<Map<String, Object>> results = h.createQuery(query).bind(USERNAME, username).list(2);
        if ((results == null) || results.isEmpty()) {
            final String $1 = getPasswordEncoder().encode(credentials.getPassword(), getPasswordEncoder());
            throw new AccountNotFoundException("No account found for: " + username);
        } else if (results.size() > 1) {
            final String $1 = getPasswordEncoder().encode(credentials.getPassword(), getPasswordEncoder());
            throw new MultipleAccountsFoundException("Too many accounts found for: " + username);
        } else {
            final Map<String, Object> result = results.get(0);
            final String expectedPassword = getPasswordEncoder().encode(credentials.getPassword());
            final String returnedPassword = ((String) (result.get(PASSWORD)));
            if (CommonHelper.areNotEquals(returnedPassword, expectedPassword)) {
                throw new BadCredentialsException("Bad credentials for: " + username);
            } else {
                final DbProfile profile = createProfile(username, attributes.split(","), result);
                credentials.setUserProfile(profile);
            }
        }
    } catch (final TechnicalException e) {
        logger.debug("Authentication error", e);
        throw e;
    } catch (final RuntimeException e) {
        throw new TechnicalException("Cannot fetch username / password from DB", e);
    } finally {
        if (h != null) {
            h.close();
        }
    }
}