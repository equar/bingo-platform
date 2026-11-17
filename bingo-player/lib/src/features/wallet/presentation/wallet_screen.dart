import 'dart:async';
import 'package:bingo_player/src/features/support/presentation/support_shortcut.dart';
import 'package:bingo_player/src/core/widgets/responsive_content.dart';
import 'dart:math';

import 'package:bingo_player/l10n/app_localizations.dart';
import 'package:bingo_player/src/core/models/page_result.dart';
import 'package:bingo_player/src/core/network/api_error.dart';
import 'package:bingo_player/src/core/widgets/app_status_view.dart';
import 'package:bingo_player/src/core/widgets/section_card.dart';
import 'package:bingo_player/src/features/wallet/data/wallet_repository.dart';
import 'package:bingo_player/src/features/wallet/domain/wallet_models.dart';
import 'package:bingo_player/src/features/wallet/presentation/wallet_transaction_labels.dart';
import 'package:bingo_player/src/features/auth/data/auth_repository.dart';
import 'package:bingo_player/src/features/auth/data/auth_models.dart';
import 'package:bingo_player/src/features/auth/data/auth_session_controller.dart';
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:uuid/uuid.dart';

final walletBalanceProvider = FutureProvider.autoDispose<WalletBalance>((
  ref,
) async {
  return ref.read(walletRepositoryProvider).getBalance();
});

final walletTransactionsProvider = FutureProvider.autoDispose
    .family<PageResult<WalletTransaction>, int>((ref, page) async {
      return ref
          .read(walletRepositoryProvider)
          .getTransactions(page: page, size: 10);
    });

final walletDepositsProvider = FutureProvider.autoDispose
    .family<PageResult<DepositClaim>, int>((ref, page) async {
      return ref
          .read(walletRepositoryProvider)
          .getDeposits(page: page, size: 10);
    });

final walletWithdrawalsProvider = FutureProvider.autoDispose
    .family<PageResult<WalletWithdrawal>, int>(
      (ref, page) async => ref
          .read(walletRepositoryProvider)
          .getWithdrawals(page: page, size: 10),
    );

final walletTransfersProvider = FutureProvider.autoDispose
    .family<PageResult<WalletOwnTransfer>, int>(
      (ref, page) async => ref
          .read(walletRepositoryProvider)
          .getOwnTransfers(page: page, size: 10),
    );

class WalletScreen extends ConsumerStatefulWidget {
  const WalletScreen({super.key, this.initialTab = 0});

  final int initialTab;

  @override
  ConsumerState<WalletScreen> createState() => _WalletScreenState();
}

class _WalletScreenState extends ConsumerState<WalletScreen>
    with SingleTickerProviderStateMixin, WidgetsBindingObserver {
  static const _walletRefreshInterval = Duration(seconds: 15);

  int _transactionPage = 0;
  int _depositPage = 0;
  Timer? _walletRefreshTimer;
  bool _isWalletVisible = false;
  bool _isAppActive = true;
  PaymentInstructions? _instructions;
  bool _submittingDeposit = false;
  bool _showDepositForm = false;
  final _depositFormKey = GlobalKey<FormState>();
  final _depositAmountController = TextEditingController();
  final _depositReferenceController = TextEditingController();
  DepositRequest? _pendingDeposit;
  String? _pendingDepositIdempotencyKey;
  bool _depositOutcomeUnknown = false;
  String? _depositError;
  ApiError? _depositApiError;
  int _selectedTabIndex = 0;
  late final TabController _tabController = TabController(
    length: 4,
    initialIndex: widget.initialTab.clamp(0, 3).toInt(),
    vsync: this,
  );

  @override
  void initState() {
    super.initState();
    _selectedTabIndex = _tabController.index;
    _tabController.addListener(_handleTabChanged);
    WidgetsBinding.instance.addObserver(this);
    _walletRefreshTimer = Timer.periodic(_walletRefreshInterval, (_) {
      if (_isWalletVisible && _isAppActive) {
        _refreshWalletData();
      }
    });
  }

  void _handleTabChanged() {
    if (_tabController.index == _selectedTabIndex) return;
    if (mounted) {
      setState(() {
        _selectedTabIndex = _tabController.index;
        if (_selectedTabIndex != 1) _showDepositForm = false;
      });
    }
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final isVisible = TickerMode.valuesOf(context).enabled;
    if (isVisible && !_isWalletVisible) {
      _isWalletVisible = true;
      _refreshWalletData();
      unawaited(_refreshCurrentProfile());
    } else if (!isVisible) {
      _isWalletVisible = false;
    }
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    _isAppActive = state == AppLifecycleState.resumed;
    if (_isAppActive && _isWalletVisible) {
      _refreshWalletData();
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _tabController.removeListener(_handleTabChanged);
    _walletRefreshTimer?.cancel();
    _tabController.dispose();
    _depositAmountController.dispose();
    _depositReferenceController.dispose();
    super.dispose();
  }

  Future<void> _confirmDeposit(String currency) async {
    if (_depositOutcomeUnknown && _pendingDeposit != null) {
      await _submitPendingDeposit();
    } else {
      await _submitDepositForm(currency);
    }
  }

  void _cancelDeposit() {
    if (_depositOutcomeUnknown && _pendingDeposit != null) return;
    setState(() {
      _depositAmountController.clear();
      _depositReferenceController.clear();
      _depositError = null;
      _depositApiError = null;
      _pendingDeposit = null;
      _pendingDepositIdempotencyKey = null;
      _showDepositForm = false;
    });
  }

  Future<void> _submitDepositForm(String currency) async {
    if (!_depositFormKey.currentState!.validate()) return;
    final request = DepositRequest(
      submittedCashAmount: num.parse(_depositAmountController.text.trim()),
      currency: currency,
      telebirrReference: _depositReferenceController.text.trim(),
    );
    setState(() {
      _pendingDeposit = request;
      _pendingDepositIdempotencyKey = const Uuid().v4();
      _depositOutcomeUnknown = false;
      _depositError = null;
      _depositApiError = null;
    });
    await _submitPendingDeposit();
  }

  Future<void> _submitPendingDeposit() async {
    final request = _pendingDeposit;
    if (request == null || _submittingDeposit) return;
    final key = _pendingDepositIdempotencyKey ?? const Uuid().v4();
    setState(() {
      _pendingDepositIdempotencyKey = key;
      _submittingDeposit = true;
      _depositError = null;
      _depositApiError = null;
    });
    try {
      final response = await ref
          .read(walletRepositoryProvider)
          .submitDeposit(request, key);
      if (!mounted) return;
      setState(() {
        _instructions = response.paymentInstructions;
        _pendingDeposit = null;
        _pendingDepositIdempotencyKey = null;
        _depositOutcomeUnknown = false;
        _depositError = null;
        _depositAmountController.clear();
        _depositReferenceController.clear();
      });
      _refreshWalletData();
    } catch (error) {
      if (!mounted) return;
      final parsed = normalizeApiError(error);
      setState(() {
        _depositApiError = parsed;
        _depositError = parsed.cancelled
            ? null
            : parsed.userMessage(AppLocalizations.of(context)!);
        _depositOutcomeUnknown = parsed.outcomeUnknown;
        if (!parsed.outcomeUnknown) _pendingDepositIdempotencyKey = null;
      });
    } finally {
      if (mounted) {
        setState(() => _submittingDeposit = false);
      }
    }
  }

  void _refreshWalletData() {
    ref.invalidate(walletBalanceProvider);
    ref.invalidate(walletTransactionsProvider(_transactionPage));
    ref.invalidate(walletDepositsProvider(_depositPage));
    ref.invalidate(walletWithdrawalsProvider(0));
  }

  Future<void> _refreshCurrentProfile() async {
    final session = ref.read(authSessionControllerProvider).session;
    if (session == null) return;
    try {
      final profile = await ref.read(authRepositoryProvider).me();
      if (!mounted) return;
      await ref
          .read(authSessionControllerProvider.notifier)
          .updateUser(profile, expectedAccessToken: session.accessToken);
    } catch (_) {}
  }

  @override
  Widget build(BuildContext context) {
    final localizations = AppLocalizations.of(context)!;
    final balanceAsync = ref.watch(walletBalanceProvider);
    final walletBalance = balanceAsync.asData?.value;
    final transactionsAsync = ref.watch(
      walletTransactionsProvider(_transactionPage),
    );
    final depositsAsync = ref.watch(walletDepositsProvider(_depositPage));
    final session = ref.watch(authSessionControllerProvider).session;
    final referralCode = session?.user.referralCode ?? '';
    final bannerHeight = walletBalance == null
        ? 0.0
        : _WalletBalanceBanner.heightFor(
            textScale: MediaQuery.textScalerOf(context).scale(14) / 14,
            hasReferralCode: referralCode.isNotEmpty,
          );

    return Scaffold(
      appBar: AppBar(
        title: const SizedBox.shrink(),
        actions: const [SupportShortcut()],
        bottom: PreferredSize(
          preferredSize: Size.fromHeight(48 + bannerHeight),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              if (walletBalance != null)
                _WalletBalanceBanner(
                  balance: walletBalance,
                  referralCode: referralCode,
                ),
              TabBar(
                isScrollable: true,
                tabAlignment: TabAlignment.start,
                controller: _tabController,
                onTap: (index) => setState(() {
                  _selectedTabIndex = index;
                  if (index != 1) _showDepositForm = false;
                }),
                tabs: [
                  Tab(text: localizations.walletTransactionsTab),
                  Tab(text: localizations.walletDepositsTab),
                  Tab(text: localizations.walletTransfersTab),
                  const Tab(text: 'Withdrawals'),
                ],
              ),
            ],
          ),
        ),
      ),
      body: ResponsiveContent(
        child: balanceAsync.when(
          loading: () => Center(child: Text(localizations.loading)),
          error: (error, stack) => AppStatusView(
            title: localizations.errorGeneric,
            message:
                userFacingApiError(error, localizations) ??
                localizations.errorGeneric,
            onAction: () => ref.invalidate(walletBalanceProvider),
          ),
          data: (balance) {
            return ListView(
              padding: const EdgeInsets.all(16),
              children: [
                if (_selectedTabIndex == 1 && !_showDepositForm) ...[
                  SectionCard(
                    child: SizedBox(
                      width: double.infinity,
                      child: FilledButton.icon(
                        key: const ValueKey('wallet-open-deposit-form-button'),
                        onPressed: () =>
                            setState(() => _showDepositForm = true),
                        icon: const Icon(Icons.add_rounded),
                        label: Text(localizations.submitDeposit),
                      ),
                    ),
                  ),
                  const SizedBox(height: 16),
                ],
                if (_selectedTabIndex == 1 && _showDepositForm) ...[
                  SectionCard(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const SizedBox(height: 16),
                        Text(localizations.balanceDisclaimer),
                        const SizedBox(height: 12),
                        Form(
                          key: _depositFormKey,
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              TextFormField(
                                key: const ValueKey('deposit-amount-field'),
                                controller: _depositAmountController,
                                enabled: !_submittingDeposit,
                                keyboardType:
                                    const TextInputType.numberWithOptions(
                                      decimal: true,
                                    ),
                                decoration: InputDecoration(
                                  labelText:
                                      '${localizations.amountLabel} (${balance.currency})',
                                  errorText: fieldErrorMessage(
                                    _depositApiError,
                                    'submittedCashAmount',
                                    localizations,
                                  ),
                                ),
                                validator: (value) {
                                  final text = value?.trim() ?? '';
                                  if (text.isEmpty) {
                                    return localizations.requiredField;
                                  }
                                  final amount = num.tryParse(text);
                                  if (amount == null || !amount.isFinite) {
                                    return localizations.errorInvalidAmount;
                                  }
                                  if (amount <= 0) {
                                    return localizations.depositAmountPositive;
                                  }
                                  return null;
                                },
                              ),
                              const SizedBox(height: 12),
                              TextFormField(
                                key: const ValueKey('deposit-reference-field'),
                                controller: _depositReferenceController,
                                enabled: !_submittingDeposit,
                                decoration: InputDecoration(
                                  labelText:
                                      localizations.telebirrReferenceLabel,
                                  errorText: fieldErrorMessage(
                                    _depositApiError,
                                    'telebirrReference',
                                    localizations,
                                  ),
                                ),
                                validator: (value) =>
                                    value == null || value.trim().isEmpty
                                    ? localizations.requiredField
                                    : null,
                              ),
                              if (_depositError != null) ...[
                                const SizedBox(height: 8),
                                Text(
                                  _depositError!,
                                  style: TextStyle(
                                    color: Theme.of(context).colorScheme.error,
                                  ),
                                ),
                              ],
                              const SizedBox(height: 12),
                              Wrap(
                                spacing: 12,
                                children: [
                                  OutlinedButton(
                                    onPressed:
                                        _submittingDeposit ||
                                            _depositOutcomeUnknown
                                        ? null
                                        : _cancelDeposit,
                                    child: Text(localizations.cancel),
                                  ),
                                  FilledButton(
                                    key: const ValueKey(
                                      'wallet-confirm-deposit-button',
                                    ),
                                    onPressed: _submittingDeposit
                                        ? null
                                        : () =>
                                              _confirmDeposit(balance.currency),
                                    child: _submittingDeposit
                                        ? const SizedBox(
                                            width: 18,
                                            height: 18,
                                            child: CircularProgressIndicator(
                                              strokeWidth: 2,
                                            ),
                                          )
                                        : Text(
                                            _depositOutcomeUnknown
                                                ? localizations.retry
                                                : localizations.confirm,
                                          ),
                                  ),
                                ],
                              ),
                            ],
                          ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 16),
                  if (_instructions != null) ...[
                    SectionCard(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            localizations.telebirrInstructionsTitle,
                            style: Theme.of(context).textTheme.titleMedium,
                          ),
                          const SizedBox(height: 8),
                          Text(_instructions!.providerName),
                          Text(_instructions!.businessAccount),
                          const SizedBox(height: 8),
                          Text(_instructions!.paymentInstructions),
                        ],
                      ),
                    ),
                    const SizedBox(height: 16),
                  ],
                ],
                SizedBox(
                  height:
                      (MediaQuery.sizeOf(context).height *
                              (_selectedTabIndex == 0 ? .78 : .65))
                          .clamp(360.0, 640.0)
                          .toDouble(),
                  child: TabBarView(
                    controller: _tabController,
                    children: [
                      _TransactionsPane(
                        asyncValue: transactionsAsync,
                        page: _transactionPage,
                        canGoPrevious: _transactionPage > 0,
                        canGoNext:
                            transactionsAsync.asData?.value.content.length ==
                            10,
                        onPrev: () => setState(() => _transactionPage -= 1),
                        onNext: () => setState(() => _transactionPage += 1),
                        localizations: localizations,
                      ),
                      _DepositsPane(
                        asyncValue: depositsAsync,
                        page: _depositPage,
                        canGoPrevious: _depositPage > 0,
                        canGoNext:
                            depositsAsync.asData?.value.content.length == 10,
                        onPrev: () => setState(() => _depositPage -= 1),
                        onNext: () => setState(() => _depositPage += 1),
                        localizations: localizations,
                      ),
                      _TransfersPane(
                        currency: balance.currency,
                        onCompleted: _refreshWalletData,
                      ),
                      _WithdrawalsPane(
                        currency: balance.currency,
                        onCompleted: _refreshWalletData,
                      ),
                    ],
                  ),
                ),
              ],
            );
          },
        ),
      ),
    );
  }
}

class _WithdrawalsPane extends ConsumerStatefulWidget {
  const _WithdrawalsPane({required this.currency, required this.onCompleted});
  final String currency;
  final VoidCallback onCompleted;
  @override
  ConsumerState<_WithdrawalsPane> createState() => _WithdrawalsPaneState();
}

class _WithdrawalsPaneState extends ConsumerState<_WithdrawalsPane> {
  final _formKey = GlobalKey<FormState>();
  final _amount = TextEditingController();
  final _name = TextEditingController();
  final _phone = TextEditingController();
  final _bank = TextEditingController();
  final _account = TextEditingController();
  String _type = 'TELEBIRR';
  bool _busy = false;
  bool _showForm = false;
  String? _error;
  int _page = 0;

  void _populateTelebirrDetails(UserProfile? user) {
    if (_type != 'TELEBIRR' || user == null) return;
    _name.text = user.displayName;
    _phone.text = user.phoneNumber;
  }
  @override
  void dispose() {
    _amount.dispose();
    _name.dispose();
    _phone.dispose();
    _bank.dispose();
    _account.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) return;
    setState(() => _busy = true);
    try {
      await ref
          .read(walletRepositoryProvider)
          .submitWithdrawal(
            WithdrawalRequest(
              amount: num.parse(_amount.text),
              currency: widget.currency,
              destinationType: _type,
              accountName: _name.text.trim(),
              phoneNumber: _type == 'TELEBIRR' ? _phone.text.trim() : null,
              bankName: _type == 'BANK' ? _bank.text.trim() : null,
              accountNumber: _type == 'BANK' ? _account.text.trim() : null,
            ),
            const Uuid().v4(),
          );
      if (!mounted) return;
      setState(() {
        _amount.clear();
        _name.clear();
        _phone.clear();
        _bank.clear();
        _account.clear();
        _showForm = false;
      });
      ref.invalidate(walletWithdrawalsProvider(_page));
      widget.onCompleted();
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text(
            'Withdrawal request submitted. Funds are reserved while it is reviewed.',
          ),
        ),
      );
    } catch (e) {
      if (mounted) {
        setState(
          () => _error =
              userFacingApiError(e, AppLocalizations.of(context)!) ??
              'Unable to submit withdrawal request.',
        );
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final async = ref.watch(walletWithdrawalsProvider(_page));
    final user = ref.watch(authSessionControllerProvider).session?.user;
    _populateTelebirrDetails(user);
    return ListView(
      padding: const EdgeInsets.all(12),
      children: [
        if (!_showForm)
          SectionCard(
            child: SizedBox(
              width: double.infinity,
              child: FilledButton.icon(
                key: const ValueKey('wallet-open-withdrawal-form-button'),
                onPressed: () => setState(() {
                  _populateTelebirrDetails(user);
                  _showForm = true;
                }),
                icon: const Icon(Icons.north_east_rounded),
                label: const Text('Submit withdrawal request'),
              ),
            ),
          ),
        if (_showForm)
          SectionCard(
            child: Form(
              key: _formKey,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Request a withdrawal',
                    style: Theme.of(context).textTheme.titleMedium,
                  ),
                  const SizedBox(height: 8),
                  const Text(
                    'Requests reserve available cash. Support will transfer it and update the status.',
                  ),
                  DropdownButtonFormField<String>(
                    initialValue: _type,
                    decoration: const InputDecoration(labelText: 'Send to'),
                    items: const [
                      DropdownMenuItem(
                        value: 'TELEBIRR',
                        child: Text('Telebirr'),
                      ),
                      DropdownMenuItem(
                        value: 'BANK',
                        child: Text('Bank account'),
                      ),
                    ],
                    onChanged: _busy
                        ? null
                        : (v) => setState(() {
                            _type = v ?? 'TELEBIRR';
                            _populateTelebirrDetails(user);
                          }),
                  ),
                  TextFormField(
                    controller: _amount,
                    keyboardType: const TextInputType.numberWithOptions(
                      decimal: true,
                    ),
                    decoration: InputDecoration(
                      labelText: 'Amount (${widget.currency})',
                    ),
                    validator: (v) {
                      final n = num.tryParse(v ?? '');
                      return n == null || n <= 0
                          ? 'Enter an amount greater than zero'
                          : null;
                    },
                  ),
                  TextFormField(
                    controller: _name,
                    readOnly: _type == 'TELEBIRR',
                    decoration: const InputDecoration(
                      labelText: 'Account holder name',
                    ),
                    validator: (v) =>
                        (v == null || v.trim().isEmpty) ? 'Required' : null,
                  ),
                  if (_type == 'TELEBIRR')
                    TextFormField(
                      controller: _phone,
                      readOnly: true,
                      decoration: const InputDecoration(
                        labelText: 'Telebirr phone number',
                      ),
                      validator: (v) =>
                          (v == null || v.trim().isEmpty) ? 'Required' : null,
                    ),
                  if (_type == 'BANK') ...[
                    TextFormField(
                      controller: _bank,
                      decoration: const InputDecoration(labelText: 'Bank name'),
                      validator: (v) =>
                          (v == null || v.trim().isEmpty) ? 'Required' : null,
                    ),
                    TextFormField(
                      controller: _account,
                      decoration: const InputDecoration(
                        labelText: 'Account number',
                      ),
                      validator: (v) =>
                          (v == null || v.trim().isEmpty) ? 'Required' : null,
                    ),
                  ],
                  if (_error != null)
                    Text(
                      _error!,
                      style: TextStyle(
                        color: Theme.of(context).colorScheme.error,
                      ),
                    ),
                  const SizedBox(height: 8),
                  FilledButton(
                    key: const ValueKey('wallet-submit-withdrawal-button'),
                    onPressed: _busy ? null : _submit,
                    child: _busy
                        ? const SizedBox(
                            width: 18,
                            height: 18,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          )
                        : const Text('Submit withdrawal request'),
                  ),
                  const SizedBox(height: 8),
                  OutlinedButton(
                    onPressed: _busy
                        ? null
                        : () => setState(() {
                            _showForm = false;
                            _amount.clear();
                            _name.clear();
                            _phone.clear();
                            _bank.clear();
                            _account.clear();
                            _error = null;
                          }),
                    child: Text(AppLocalizations.of(context)!.cancel),
                  ),
                ],
              ),
            ),
          ),
        const SizedBox(height: 12),
        Text(
          'Your withdrawal requests',
          style: Theme.of(context).textTheme.titleMedium,
        ),
        async.when(
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (e, s) => Text(
            userFacingApiError(e, AppLocalizations.of(context)!) ??
                'Unable to load withdrawals',
          ),
          data: (result) => Column(
            children: [
              for (final r in result.content)
                Card(
                  child: ListTile(
                    title: Text(
                      '${r.amount} ${r.currency} · ${r.destinationType}',
                    ),
                    subtitle: Text(
                      '${r.accountName} · ${r.status}${r.transferReference == null ? '' : ' · Ref ${r.transferReference}'}${r.reviewNote == null ? '' : ' · ${r.reviewNote}'}',
                    ),
                    trailing: Text('${r.createdAt.toLocal()}'),
                  ),
                ),
              Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  IconButton(
                    onPressed: _page > 0 ? () => setState(() => _page--) : null,
                    icon: const Icon(Icons.chevron_left),
                  ),
                  Text('Page ${_page + 1}'),
                  IconButton(
                    onPressed: result.content.length == 10
                        ? () => setState(() => _page++)
                        : null,
                    icon: const Icon(Icons.chevron_right),
                  ),
                ],
              ),
            ],
          ),
        ),
      ],
    );
  }
}

class _TransfersPane extends ConsumerStatefulWidget {
  const _TransfersPane({required this.currency, required this.onCompleted});

  final String currency;
  final VoidCallback onCompleted;

  @override
  ConsumerState<_TransfersPane> createState() => _TransfersPaneState();
}

class _TransfersPaneState extends ConsumerState<_TransfersPane> {
  final TextEditingController _phoneController = TextEditingController();
  final TextEditingController _amountController = TextEditingController();
  final TextEditingController _noteController = TextEditingController();
  bool _lookingUp = false;
  bool _submitting = false;
  bool _showTransferForm = false;
  WalletRecipientLookup? _recipient;
  String? _errorMessage;
  ApiError? _apiError;
  WalletTransferRequest? _pendingTransfer;
  String? _transferIdempotencyKey;
  bool _transferOutcomeUnknown = false;

  void _cancelTransferForm() {
    if (_submitting || _transferOutcomeUnknown) return;
    setState(() {
      _showTransferForm = false;
      _phoneController.clear();
      _amountController.clear();
      _noteController.clear();
      _recipient = null;
      _errorMessage = null;
      _apiError = null;
      _pendingTransfer = null;
      _transferIdempotencyKey = null;
    });
  }

  @override
  void dispose() {
    _phoneController.dispose();
    _amountController.dispose();
    _noteController.dispose();
    super.dispose();
  }

  Future<void> _lookupRecipient() async {
    final phone = _phoneController.text.trim();
    if (phone.isEmpty) {
      setState(
        () => _errorMessage = AppLocalizations.of(
          context,
        )!.recipientPhoneRequired,
      );
      return;
    }

    setState(() {
      _lookingUp = true;
      _errorMessage = null;
      _apiError = null;
      _recipient = null;
    });

    try {
      final lookup = await ref
          .read(walletRepositoryProvider)
          .lookupRecipient(phone);
      if (!mounted) return;
      setState(() => _recipient = lookup);
    } catch (error) {
      if (!mounted) return;
      final parsed = normalizeApiError(error);
      setState(() {
        _apiError = parsed;
        _errorMessage = parsed.cancelled
            ? null
            : parsed.userMessage(AppLocalizations.of(context)!);
      });
    } finally {
      if (mounted) {
        setState(() => _lookingUp = false);
      }
    }
  }

  Future<void> _submitTransfer() async {
    if (_submitting) return;
    if (_pendingTransfer == null) {
      final amount = num.tryParse(_amountController.text.trim());
      if (_recipient == null) {
        setState(
          () => _errorMessage = AppLocalizations.of(
            context,
          )!.lookupRecipientFirst,
        );
        return;
      }
      if (amount == null || amount <= 0) {
        setState(
          () => _errorMessage = AppLocalizations.of(
            context,
          )!.transferAmountInvalid,
        );
        return;
      }
      _pendingTransfer = WalletTransferRequest(
        recipientPhoneNumber: _recipient!.normalizedPhone,
        amount: amount,
        currency: widget.currency,
        note: _noteController.text.trim().isEmpty
            ? null
            : _noteController.text.trim(),
      );
      _transferIdempotencyKey = const Uuid().v4();
    }
    final request = _pendingTransfer!;
    final recipient = _recipient!;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: Text(AppLocalizations.of(context)!.sendTransfer),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(AppLocalizations.of(context)!.recipientPreviewTitle),
            const SizedBox(height: 8),
            Text(recipient.displayName),
            Text(recipient.normalizedPhone),
            const SizedBox(height: 12),
            Text('${request.amount} ${request.currency}'),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(false),
            child: Text(AppLocalizations.of(context)!.cancel),
          ),
          FilledButton(
            onPressed: () => Navigator.of(dialogContext).pop(true),
            child: Text(AppLocalizations.of(context)!.confirm),
          ),
        ],
      ),
    );
    if (!mounted) return;
    if (confirmed != true) {
      if (!_transferOutcomeUnknown) {
        setState(() {
          _pendingTransfer = null;
          _transferIdempotencyKey = null;
        });
      }
      return;
    }

    final idempotencyKey = _transferIdempotencyKey ?? const Uuid().v4();

    setState(() {
      _submitting = true;
      _transferIdempotencyKey = idempotencyKey;
      _errorMessage = null;
      _apiError = null;
    });

    try {
      await ref
          .read(walletRepositoryProvider)
          .submitTransfer(request, idempotencyKey);
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(AppLocalizations.of(context)!.transferSubmitted),
        ),
      );
      setState(() {
        _showTransferForm = false;
        _recipient = null;
        _pendingTransfer = null;
        _transferIdempotencyKey = null;
        _transferOutcomeUnknown = false;
        _phoneController.clear();
        _amountController.clear();
        _noteController.clear();
      });
      widget.onCompleted();
      ref.invalidate(walletTransfersProvider);
    } catch (error) {
      if (!mounted) return;
      final parsed = normalizeApiError(error);
      setState(() {
        _apiError = parsed;
        _errorMessage = parsed.cancelled
            ? null
            : parsed.userMessage(AppLocalizations.of(context)!);
        _transferOutcomeUnknown = parsed.outcomeUnknown;
        if (!parsed.outcomeUnknown) {
          _pendingTransfer = null;
          _transferIdempotencyKey = null;
        }
      });
    } finally {
      if (mounted) {
        setState(() => _submitting = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final localizations = AppLocalizations.of(context)!;
    final phoneError = fieldErrorMessage(
      _apiError,
      'recipientPhoneNumber',
      localizations,
    );
    final amountError = fieldErrorMessage(_apiError, 'amount', localizations);
    final hasInlineError = phoneError != null || amountError != null;
    if (!_showTransferForm) {
      return SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            SectionCard(
              child: SizedBox(
                width: double.infinity,
                child: FilledButton.icon(
                  key: const ValueKey('wallet-open-transfer-form-button'),
                  onPressed: () => setState(() => _showTransferForm = true),
                  icon: const Icon(Icons.north_east_rounded),
                  label: Text(localizations.submitTransfer),
                ),
              ),
            ),
            const SizedBox(height: 16),
            const _PlayerTransferHistory(),
          ],
        ),
      );
    }
    return SingleChildScrollView(
      child: Column(
        children: [
          SectionCard(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  localizations.sendMoneyTitle,
                  style: Theme.of(context).textTheme.titleLarge,
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: _phoneController,
                  keyboardType: TextInputType.phone,
                  readOnly: _transferOutcomeUnknown,
                  decoration: InputDecoration(
                    labelText: localizations.recipientPhoneLabel,
                    errorText: phoneError,
                  ),
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: _amountController,
                  keyboardType: TextInputType.number,
                  readOnly: _transferOutcomeUnknown,
                  decoration: InputDecoration(
                    labelText:
                        '${localizations.amountLabel} (${widget.currency})',
                    errorText: amountError,
                  ),
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: _noteController,
                  readOnly: _transferOutcomeUnknown,
                  decoration: InputDecoration(
                    labelText: localizations.transferNoteOptionalLabel,
                  ),
                  maxLines: 2,
                ),
                const SizedBox(height: 12),
                Wrap(
                  spacing: 12,
                  runSpacing: 12,
                  children: [
                    OutlinedButton(
                      onPressed: _submitting || _transferOutcomeUnknown
                          ? null
                          : _cancelTransferForm,
                      child: Text(localizations.cancel),
                    ),
                    OutlinedButton(
                      onPressed: _lookingUp || _transferOutcomeUnknown
                          ? null
                          : _lookupRecipient,
                      child: Text(
                        _lookingUp
                            ? localizations.lookingUpRecipient
                            : localizations.lookupRecipient,
                      ),
                    ),
                    FilledButton(
                      onPressed: _submitting ? null : _submitTransfer,
                      child: Text(
                        _submitting
                            ? localizations.sendingTransfer
                            : _transferOutcomeUnknown
                            ? localizations.retry
                            : localizations.sendTransfer,
                      ),
                    ),
                  ],
                ),
                if (_errorMessage != null && !hasInlineError) ...[
                  const SizedBox(height: 12),
                  Text(
                    _errorMessage!,
                    style: TextStyle(
                      color: Theme.of(context).colorScheme.error,
                    ),
                  ),
                ],
              ],
            ),
          ),
          const SizedBox(height: 16),
          SectionCard(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  localizations.recipientPreviewTitle,
                  style: Theme.of(context).textTheme.titleMedium,
                ),
                const SizedBox(height: 8),
                if (_recipient == null)
                  Text(localizations.lookupRecipientHint)
                else ...[
                  Text(_recipient!.displayName),
                  Text(_recipient!.normalizedPhone),
                ],
              ],
            ),
          ),
          const SizedBox(height: 16),
          const _PlayerTransferHistory(),
        ],
      ),
    );
  }
}

class _PlayerTransferHistory extends ConsumerStatefulWidget {
  const _PlayerTransferHistory();

  @override
  ConsumerState<_PlayerTransferHistory> createState() =>
      _PlayerTransferHistoryState();
}

class _PlayerTransferHistoryState
    extends ConsumerState<_PlayerTransferHistory> {
  int _page = 0;

  @override
  Widget build(BuildContext context) {
    final localizations = AppLocalizations.of(context)!;
    final history = ref.watch(walletTransfersProvider(_page));
    return SectionCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            localizations.transferHistoryTitle,
            style: Theme.of(context).textTheme.titleMedium,
          ),
          const SizedBox(height: 8),
          history.when(
            loading: () => Padding(
              padding: const EdgeInsets.symmetric(vertical: 16),
              child: Center(child: Text(localizations.loading)),
            ),
            error: (error, stack) => Text(
              userFacingApiError(error, localizations) ??
                  localizations.errorGeneric,
              style: TextStyle(color: Theme.of(context).colorScheme.error),
            ),
            data: (result) {
              if (result.content.isEmpty && _page == 0) {
                return Padding(
                  padding: const EdgeInsets.symmetric(vertical: 12),
                  child: Text(localizations.transferHistoryEmpty),
                );
              }
              return Column(
                children: [
                  for (final transfer in result.content)
                    ListTile(
                      contentPadding: EdgeInsets.zero,
                      leading: Icon(
                        Icons.north_east_rounded,
                        color: Theme.of(context).colorScheme.primary,
                      ),
                      title: Row(
                        children: [
                          Expanded(
                            child: Text(
                              '${transfer.amount} ${transfer.currency}',
                              style: Theme.of(context).textTheme.titleSmall,
                            ),
                          ),
                          Text(
                            transfer.status,
                            style: Theme.of(context).textTheme.labelSmall,
                          ),
                        ],
                      ),
                      subtitle: Text(
                        '${localizations.recipientPhoneLabel}: ${transfer.recipientPhoneNumber}\n${DateFormat.yMMMd().add_jm().format(transfer.createdAt.toLocal())}',
                      ),
                    ),
                  if (result.totalPages > 1)
                    Row(
                      mainAxisAlignment: MainAxisAlignment.end,
                      children: [
                        IconButton(
                          tooltip: localizations.previous,
                          onPressed: _page > 0
                              ? () => setState(() => _page--)
                              : null,
                          icon: const Icon(Icons.chevron_left),
                        ),
                        Text('${_page + 1} / ${result.totalPages}'),
                        IconButton(
                          tooltip: localizations.next,
                          onPressed: _page + 1 < result.totalPages
                              ? () => setState(() => _page++)
                              : null,
                          icon: const Icon(Icons.chevron_right),
                        ),
                      ],
                    ),
                ],
              );
            },
          ),
        ],
      ),
    );
  }
}

class _TransactionsPane extends StatelessWidget {
  const _TransactionsPane({
    required this.asyncValue,
    required this.page,
    required this.canGoPrevious,
    required this.canGoNext,
    required this.onPrev,
    required this.onNext,
    required this.localizations,
  });

  final AsyncValue<PageResult<WalletTransaction>> asyncValue;
  final int page;
  final bool canGoPrevious;
  final bool canGoNext;
  final VoidCallback onPrev;
  final VoidCallback onNext;
  final AppLocalizations localizations;

  @override
  Widget build(BuildContext context) {
    return asyncValue.when(
      loading: () => Center(child: Text(localizations.loading)),
      error: (error, stack) => AppStatusView(
        title: localizations.errorGeneric,
        message:
            userFacingApiError(error, localizations) ??
            localizations.errorGeneric,
      ),
      data: (result) {
        if (result.content.isEmpty) {
          return AppStatusView(
            title: localizations.emptyState,
            message: localizations.transactionHistoryEmpty,
          );
        }
        return Column(
          children: [
            Expanded(
              child: ListView.builder(
                itemCount: result.content.length,
                itemBuilder: (context, index) {
                  final entry = result.content[index];
                  final campaignReward = isWalletCampaignReward(
                    entry.transactionType,
                  );
                  final transactionDate = DateFormat.yMMMd().add_jm().format(
                    entry.createdAt.toLocal(),
                  );
                  return Padding(
                    padding: const EdgeInsets.only(bottom: 12),
                    child: SectionCard(
                      padding: const EdgeInsets.all(12),
                      child: ListTile(
                        title: Text(
                          campaignReward
                              ? walletTransactionCampaignName(
                                  localizations,
                                  entry.transactionType,
                                  entry.note,
                                )
                              : '${walletTransactionTypeLabel(localizations, entry.transactionType)} • ${entry.amount} ${entry.currency}',
                        ),
                        subtitle: Text(
                          '${entry.amount} ${entry.currency} · $transactionDate',
                        ),
                        trailing: Text('${entry.balanceAfter}'),
                      ),
                    ),
                  );
                },
              ),
            ),
            Wrap(
              alignment: WrapAlignment.spaceBetween,
              crossAxisAlignment: WrapCrossAlignment.center,
              spacing: 12,
              runSpacing: 8,
              children: [
                Text(
                  '${localizations.walletPageLabel} ${page + 1}/${max(1, result.totalPages)}',
                ),
                Wrap(
                  spacing: 4,
                  runSpacing: 4,
                  children: [
                    TextButton(
                      onPressed: canGoPrevious ? onPrev : null,
                      child: Text(localizations.previous),
                    ),
                    TextButton(
                      onPressed: canGoNext ? onNext : null,
                      child: Text(localizations.next),
                    ),
                  ],
                ),
              ],
            ),
          ],
        );
      },
    );
  }
}

class _DepositsPane extends StatelessWidget {
  const _DepositsPane({
    required this.asyncValue,
    required this.page,
    required this.canGoPrevious,
    required this.canGoNext,
    required this.onPrev,
    required this.onNext,
    required this.localizations,
  });

  final AsyncValue<PageResult<DepositClaim>> asyncValue;
  final int page;
  final bool canGoPrevious;
  final bool canGoNext;
  final VoidCallback onPrev;
  final VoidCallback onNext;
  final AppLocalizations localizations;

  @override
  Widget build(BuildContext context) {
    return asyncValue.when(
      loading: () => Center(child: Text(localizations.loading)),
      error: (error, stack) => AppStatusView(
        title: localizations.errorGeneric,
        message:
            userFacingApiError(error, localizations) ??
            localizations.errorGeneric,
      ),
      data: (result) {
        if (result.content.isEmpty) {
          return AppStatusView(
            title: localizations.emptyState,
            message: localizations.depositHistoryEmpty,
          );
        }
        return Column(
          children: [
            Expanded(
              child: ListView.builder(
                itemCount: result.content.length,
                itemBuilder: (context, index) {
                  final entry = result.content[index];
                  return Padding(
                    padding: const EdgeInsets.only(bottom: 12),
                    child: SectionCard(
                      padding: const EdgeInsets.all(12),
                      child: ListTile(
                        title: Text(
                          '${entry.submittedCashAmount} ${entry.currency}',
                        ),
                        subtitle: Text(
                          '${entry.normalizedTelebirrReference}\n${entry.displayStatus}',
                        ),
                        isThreeLine: true,
                        trailing: Text(
                          entry.paymentInstructions?.providerName ??
                              localizations.statusPending,
                        ),
                      ),
                    ),
                  );
                },
              ),
            ),
            Wrap(
              alignment: WrapAlignment.spaceBetween,
              crossAxisAlignment: WrapCrossAlignment.center,
              spacing: 12,
              runSpacing: 8,
              children: [
                Text(
                  '${localizations.walletPageLabel} ${page + 1}/${max(1, result.totalPages)}',
                ),
                Wrap(
                  spacing: 4,
                  runSpacing: 4,
                  children: [
                    TextButton(
                      onPressed: canGoPrevious ? onPrev : null,
                      child: Text(localizations.previous),
                    ),
                    TextButton(
                      onPressed: canGoNext ? onNext : null,
                      child: Text(localizations.next),
                    ),
                  ],
                ),
              ],
            ),
          ],
        );
      },
    );
  }
}

class _WalletBalanceBanner extends StatelessWidget {
  const _WalletBalanceBanner({
    required this.balance,
    required this.referralCode,
  });
  final WalletBalance balance;
  final String referralCode;

  static double heightFor({
    required double textScale,
    required bool hasReferralCode,
  }) => max(84.0, 68 * textScale + 12) + (hasReferralCode ? 24 * textScale : 0);

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context)!;
    return SizedBox(
      height: heightFor(
        textScale: MediaQuery.textScalerOf(context).scale(14) / 14,
        hasReferralCode: referralCode.isNotEmpty,
      ),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 5, 16, 7),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(
                    '${l.totalBalance}: ${balance.totalBalance} ${balance.currency}',
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      color: Colors.white,
                      fontSize: 17,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 4),
            Row(
              children: [
                Expanded(
                  child: _BalanceFigure(
                    label: l.availableBalanceLabel,
                    value: balance.availableBalance,
                    currency: balance.currency,
                  ),
                ),
                Expanded(
                  child: Row(
                    children: [
                      Expanded(
                        child: _BalanceFigure(
                          label: l.promotionalBalanceLabel,
                          value: balance.promotionalBalance,
                          currency: balance.currency,
                        ),
                      ),
                      Tooltip(
                        message: l.promotionalBalanceRestrictionHelp,
                        child: const Icon(
                          Icons.info_outline,
                          size: 16,
                          color: Color(0xFF9ACBFF),
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
            if (referralCode.isNotEmpty) ...[
              const SizedBox(height: 5),
              Align(
                alignment: AlignmentDirectional.centerStart,
                child: SelectableText(
                  '${l.yourReferralCode}: $referralCode',
                  key: const ValueKey('player-referral-code'),
                  maxLines: 1,
                  style: const TextStyle(
                    color: Color(0xFFDAE8FF),
                    fontSize: 12,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }
}

class _BalanceFigure extends StatelessWidget {
  const _BalanceFigure({
    required this.label,
    required this.value,
    required this.currency,
  });

  final String label;
  final num value;
  final String currency;

  @override
  Widget build(BuildContext context) => Column(
    crossAxisAlignment: CrossAxisAlignment.start,
    mainAxisSize: MainAxisSize.min,
    children: [
      Text(
        label,
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
        style: const TextStyle(color: Color(0xFFDAE8FF), fontSize: 10),
      ),
      Text(
        '$value $currency',
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
        style: const TextStyle(
          color: Colors.white,
          fontSize: 13,
          fontWeight: FontWeight.w700,
        ),
      ),
    ],
  );
}
