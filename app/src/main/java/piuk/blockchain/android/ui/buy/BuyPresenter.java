package piuk.blockchain.android.ui.buy;

import android.support.annotation.Nullable;

import javax.inject.Inject;

import piuk.blockchain.android.R;
import piuk.blockchain.android.data.exchange.BuyDataManager;
import piuk.blockchain.androidcore.data.payload.PayloadDataManager;
import piuk.blockchain.android.data.walletoptions.WalletOptionsDataManager;
import piuk.blockchain.android.ui.base.BasePresenter;
import piuk.blockchain.android.ui.base.UiState;
import piuk.blockchain.android.ui.customviews.ToastCustom;
import piuk.blockchain.android.util.AppUtil;
import timber.log.Timber;

/**
 * Created by justin on 4/27/17.
 */

public class BuyPresenter extends BasePresenter<BuyView> {

    private AppUtil appUtil;
    private BuyDataManager buyDataManager;
    private PayloadDataManager payloadDataManager;
    private WalletOptionsDataManager walletOptionsDataManager;

    @Inject
    BuyPresenter(AppUtil appUtil,
                 BuyDataManager buyDataManager,
                 PayloadDataManager payloadDataManager,
                 WalletOptionsDataManager walletOptionsDataManager) {

        this.appUtil = appUtil;
        this.buyDataManager = buyDataManager;
        this.payloadDataManager = payloadDataManager;
        this.walletOptionsDataManager = walletOptionsDataManager;
    }

    @Override
    public void onViewReady() {
        attemptPageSetup();
    }

    Boolean isNewlyCreated() {
        return appUtil.isNewlyCreated();
    }

    void reloadExchangeDate() {
        buyDataManager.reloadExchangeData();
    }

    private void attemptPageSetup() {
        getView().setUiState(UiState.LOADING);

        getCompositeDisposable().add(payloadDataManager.loadNodes()
                .subscribe(loaded -> {
                    if (loaded) {
                        getCompositeDisposable().add(
                                buyDataManager
                                        .getWebViewLoginDetails()
                                        .subscribe(
                                                webViewLoginDetails -> getView().setWebViewLoginDetails(webViewLoginDetails),
                                                Throwable::printStackTrace));
                    } else {
                        // Not set up, most likely has a second password enabled
                        if (payloadDataManager.isDoubleEncrypted()) {
                            getView().showSecondPasswordDialog();
                            getView().setUiState(UiState.EMPTY);
                        } else {
                            generateMetadataNodes();
                        }
                    }
                }));
    }

    void decryptAndGenerateMetadataNodes(@Nullable String secondPassword) {
        if (!payloadDataManager.validateSecondPassword(secondPassword)) {
            getView().showToast(R.string.invalid_password, ToastCustom.TYPE_ERROR);
            getView().showSecondPasswordDialog();
            getView().setUiState(UiState.EMPTY);
        } else {

            try {
                payloadDataManager.decryptHDWallet(secondPassword);
                getCompositeDisposable().add(
                        payloadDataManager.generateNodes()
                                .subscribe(
                                        this::attemptPageSetup,
                                        throwable -> getView().setUiState(UiState.FAILURE)));
            } catch (Exception e) {
                Timber.e(e);
            }
        }
    }

    void generateMetadataNodes() {
        getCompositeDisposable().add(
                payloadDataManager.generateNodes()
                        .subscribe(
                                this::attemptPageSetup,
                                throwable -> getView().setUiState(UiState.FAILURE)));
    }

    String getCurrentServerUrl() {
        return walletOptionsDataManager.getBuyWebviewWalletLink();
    }
}
