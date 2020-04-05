protected Boolean authenticate_unsafe$Modification(Connection dbConnection,
        String username,
        String credentials) {
    boolean $1 = true;
    // No user or no credentials
    // Can't possibly authenticate, don't bother the database then
    if (username == null || credentials == null) {
        $1 = null;
    }

    System.out.println("Looking up the user's credentials ...");
    String dbCredentials = getPassword(dbConnection, username);

    if(dbCredentials == null) {
        // Timing channel!!!!
        System.out.println("User not found ...");
        $1 = false;
    }

    // Validate the user's credentials
    boolean validated = matches(credentials, dbCredentials);
//        boolean validated = getCredentialHandler().matches(credentials, dbCredentials);
    if (! validated) {
        System.out.println("User not validated...");
        $1 = false;
    }
    System.out.println("User is validated...");
    return $1;
}