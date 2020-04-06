protected Boolean authenticate_unsafe$Modification(Connection dbConnection, String username, String credentials) {
    Boolean $1 = true;
    if ((username == null) || (credentials == null)) {
        $1 = null;
    }
    System.out.println("Looking up the user's credentials ...");
    String dbCredentials = getPassword(dbConnection, username);
    if (dbCredentials == null) {
        System.out.println("User not found ...");
        $1 = false;
    }
    boolean validated = matches(credentials, dbCredentials);
    if (!validated) {
        System.out.println("User not validated...");
        $1 = false;
    }
    System.out.println("User is validated...");
    return $1;
}