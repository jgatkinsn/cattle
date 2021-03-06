package io.cattle.platform.core.dao.impl;

import static io.cattle.platform.core.model.tables.CredentialTable.*;
import static io.cattle.platform.core.model.tables.AccountTable.*;

import org.jooq.Condition;
import org.jooq.impl.DSL;

import io.cattle.platform.core.constants.AccountConstants;
import io.cattle.platform.core.constants.CommonStatesConstants;
import io.cattle.platform.core.constants.CredentialConstants;
import io.cattle.platform.core.dao.AccountDao;
import io.cattle.platform.core.model.Account;
import io.cattle.platform.core.model.Credential;

public class AccountDaoImpl extends AbstractCoreDao implements AccountDao {

    @Override
    public Account getSystemAccount() {
        Account system = objectManager.findOne(Account.class,
                ACCOUNT.UUID, AccountConstants.SYSTEM_UUID);
        if ( system == null ) {
            throw new IllegalStateException("Failed to find system account");
        }

        return system;
    }

    @Override
    public Credential getApiKey(Account account, boolean active) {
        Condition stateCondition = DSL.trueCondition();
        if ( active ) {
            stateCondition = CREDENTIAL.STATE.eq(CommonStatesConstants.ACTIVE);
        }

        return create().selectFrom(CREDENTIAL)
            .where(CREDENTIAL.ACCOUNT_ID.eq(account.getId())
                    .and(CREDENTIAL.REMOVED.isNull())
                    .and(stateCondition)
                    .and(CREDENTIAL.KIND.eq(CredentialConstants.KIND_API_KEY)))
            .fetchAny();
    }

    @Override
    public Account findByUuid(String uuid) {
        return create()
                .selectFrom(ACCOUNT)
                .where(ACCOUNT.UUID.eq(uuid))
                .fetchOne();
    }

}