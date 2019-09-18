package com.blockchain.notifications.analytics

enum class AnalyticsEvents(
    override val event: String,
    override val params: Map<String, String> = emptyMap()
) : AnalyticsEvent {

    AccountsAndAddresses("accounts_and_addresses"),
    Backup("backup"),
    BuyBitcoin("buy_bitcoin"),
    Dashboard("dashboard"),
    Exchange("exchange"),
    ExchangeCreate("exchange_create"),
    ExchangeDetailConfirm("exchange_detail_confirm"),
    ExchangeDetailLocked("exchange_detail_locked"),
    ExchangeDetailOverview("exchange_detail_overview"),
    ExchangeExecutionError("exchange_execution_error"),
    ExchangeHistory("exchange_history"),
    KycEmail("kyc_email"),
    KycAddress("kyc_address"),
    KycComplete("kyc_complete"),
    SwapTiers("swap_tiers"),
    KycTiersLocked("kyc_tiers_locked"),
    KycTier1Complete("kyc_tier1_complete"),
    KycTier2Complete("kyc_tier2_complete"),
    KycCountry("kyc_country"),
    KycProfile("kyc_profile"),
    KycStates("kyc_states"),
    KycVerifyIdentity("kyc_verify_identity"),
    KycWelcome("kyc_welcome"),
    KycResubmission("kyc_resubmission"),
    KycSunriverStart("kyc_sunriver_start"),
    KycMoreInfo("kyc_more_info"),
    KycTiers("kyc_tiers"),
    Lockbox("lockbox"),
    Logout("logout"),
    Settings("settings"),
    Support("support"),
    WebLogin("web_login"),
    SunRiverBottomDialog("sunriver_bottom_dialog"),
    SunRiverBottomDialogClicked("sunriver_bottom_dialog_clicked"),
    SunRiverBottomDialogClickedRocket("sunriver_bottom_dialog_clicked_rocket"),
    SunRiverBottomCampaignDialog("sunriver_bottom_campaign_dialog"),
    SunRiverBottomCampaignDialogClicked("sunriver_bottom_campaign_dialog_clicked"),
    SunRiverBottomCampaignDialogClickedRocket("sunriver_bottom_campaign_dialog_clicked_rocket"),
    SunRiverBottomCampaignDialogDismissClicked("sunriver_bottom_campaign_dialog_dismiss_click"),
    CoinifyKycBottomDialog("coinify_kyc_bottom_dialog"),
    CoinifyKycBottomDialogClicked("coinify_kyc_bottom_dialog_clicked"),
    CoinifyKycBottomDialogClickedRocket("coinify_kyc_bottom_dialog_clicked_rocket"),
    CoinifyKycBottomDialogLearnMoreClicked("coinify_kyc_bottom_dialog_learn_more_clicked"),
    ClaimFreeCryptoSuccessDialog("claim_free_crypto_success_dialog"),
    ClaimFreeCryptoSuccessDialogClicked("claim_free_crypto_success_dialog_clicked"),
    ClaimFreeCryptoSuccessDialogClickedRocket("claim_free_crypto_success_clicked_rocket"),
    SwapErrorDialog("swap_error_dialog"),
    SwapErrorDialogCtaClicked("swap_error_dialog_cta_clicked"),
    SwapErrorDialogDismissClicked("swap_error_dialog_dismiss_clicked"),
    SwapInfoDialog("swap_info_dialog"),
    SwapInfoDialogViewHistory("swap_info_dialog_history_click"),
    SwapInfoDialogSwapLimits("swap_info_dialog_limits_click"),
    SwapInfoDialogSupport("swap_info_dialog_support_click"),
    BitpayAdrressScanned("bitpay_url_scanned"),
    BitpayUrlPasted("bitpay_url_pasted"),
    BitpayPaymentExpired("bitpay_payment_expired"),
    BitpayPaymentFailed("bitpay_payment_failure"),
    BitpayPaymentSucceed("bitpay_payment_success"),
    BitpayUrlDeeplink("bitpay_url_deeplink"),
    WalletCreation("wallet_creation"),
    WalletManualLogin("wallet_manual_login"),
    WalletAutoPairing("wallet_auto_pairing")
}

fun kycTierStart(tier: Int): AnalyticsEvent = object : AnalyticsEvent {
    override val event: String = "kyc_tier${tier}_start"
    override val params: Map<String, String> = emptyMap()
}

fun networkError(host: String, path: String, message: String): AnalyticsEvent = object : AnalyticsEvent {
    override val event: String
        get() = "network_error"
    override val params: Map<String, String>
        get() = mapOf("host" to host, "message" to message, "path" to path)
}

fun apiError(host: String, path: String, body: String?, requestId: String?, errorCode: Int): AnalyticsEvent =
    object : AnalyticsEvent {
        override val event: String
            get() = "api_error"
        override val params: Map<String, String>
            get() = mapOf(
                "host" to host,
                "body" to body,
                "path" to path,
                "error_code" to errorCode.toString(),
                "request_id" to requestId
            ).mapNotNull { it.value?.let { value -> it.key to value } }.toMap()
    }