package com.hypertube.persistence;

import org.hibernate.dialect.PostgreSQLDialect;
import org.hibernate.dialect.identity.IdentityColumnSupport;
import org.hibernate.dialect.identity.PostgreSQLIdentityColumnSupport;

public class SchemaExportDialect extends PostgreSQLDialect {
    @Override
    public IdentityColumnSupport getIdentityColumnSupport() {
        // Preserve the GENERATED ALWAYS identity policy of existing user IDs.
        return new PostgreSQLIdentityColumnSupport() {
            @Override
            public String getIdentityColumnString(int type) {
                return "generated always as identity";
            }
        };
    }
}
