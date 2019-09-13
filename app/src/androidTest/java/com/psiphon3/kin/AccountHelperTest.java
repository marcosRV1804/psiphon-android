package com.psiphon3.kin;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.test.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

import io.reactivex.observers.TestObserver;
import kin.sdk.KinAccount;
import kin.sdk.KinClient;
import kin.sdk.exception.OperationFailedException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AccountHelperTest {

    private KinClient kinClient;
    private KinAccount account;
    private AccountHelper accountHelper;

    @Before
    public void setUp() throws InterruptedException, OperationFailedException {
        Environment env = Environment.TEST;
        SharedPreferences sharedPreferences = InstrumentationRegistry.getTargetContext().getSharedPreferences("test", Context.MODE_PRIVATE);
        Context context = mock(Context.class);
        when(context.getApplicationContext()).thenReturn(context);
        when(context.getSharedPreferences(anyString(), anyInt())).thenReturn(sharedPreferences);

        ServerCommunicator serverCommunicator = new ServerCommunicator(env.getFriendBotServerUrl());
        kinClient = new KinClient(context, env.getKinEnvironment(), Environment.PSIPHON_APP_ID);
        ClientHelper clientHelper = new ClientHelper(kinClient, serverCommunicator);
        kinClient.clearAllAccounts();

        account = clientHelper.getAccount().blockingGet();
        accountHelper = new AccountHelper(account, serverCommunicator, env.getPsiphonWalletAddress());

        // Setup isn't finished until the account is created
        Utils.ensureAccountCreated(account);
    }

    @After
    public void tearDown() {
        kinClient.clearAllAccounts();
    }

    @Test
    public void transferOut() throws OperationFailedException {
        // Get the initial balance. OK to use an int because we won't use higher precision stuff for the transfers
        int initialBalance = account.getBalanceSync().value().intValue();
        TestObserver<Void> tester = accountHelper.transferOut(Utils.TRANSFER_AMOUNT).test();

        // Check that it finished not because of timeout but because of onComplete
        assertTrue(tester.awaitTerminalEvent(Utils.WAIT_TIME_S, TimeUnit.SECONDS));
        tester.assertComplete();

        // Check the balance has updated
        assertEquals(initialBalance - Utils.TRANSFER_AMOUNT, account.getBalanceSync().value().doubleValue(), Utils.DELTA);

        // TODO: Determine some way to check if the Psiphon wallet has been changed as well
    }

    @Test
    public void getCurrentBalance() {
        TestObserver<BigDecimal> tester = accountHelper.getCurrentBalance().test();

        // Check that it finished not because of timeout but because of onComplete
        assertTrue(tester.awaitTerminalEvent(Utils.WAIT_TIME_S, TimeUnit.SECONDS));
        tester.assertComplete();

        // Check the balance is what we expect after funding
        assertEquals(Utils.FUND_AMOUNT, tester.values().get(0).doubleValue(), Utils.DELTA);

        // Try transferring to make sure it updates
        TestObserver<Void> transferTester = accountHelper.transferOut(Utils.TRANSFER_AMOUNT).test();

        // Check that it finished not because of timeout but because of onComplete
        assertTrue(transferTester.awaitTerminalEvent(Utils.WAIT_TIME_S, TimeUnit.SECONDS));
        transferTester.assertComplete();

        tester = accountHelper.getCurrentBalance().test();

        // Check that it finished not because of timeout but because of onComplete
        assertTrue(tester.awaitTerminalEvent(Utils.WAIT_TIME_S, TimeUnit.SECONDS));
        tester.assertComplete();

        // Check the balance is what we expect after funding
        assertEquals(Utils.FUND_AMOUNT - Utils.TRANSFER_AMOUNT, tester.values().get(0).doubleValue(), Utils.DELTA);
    }
}