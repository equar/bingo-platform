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
import 'package:flutter/material.dart';
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

class WalletScreen extends ConsumerStatefulWidget {
  const WalletScreen({super.key});

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
  late final TabController _tabController = TabController(
    length: 3,
    vsync: this,
  );

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _walletRefreshTimer = Timer.periodic(_walletRefreshInterval, (_) {
      if (_isWalletVisible && _isAppActive) {
        _refreshWalletData();
      }
    });
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final isVisible = TickerMode.valuesOf(context).enabled;
    if (isVisible && !_isWalletVisible) {
      _isWalletVisible = true;
      _refreshWalletData();
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
    _walletRefreshTimer?.cancel();
    _tabController.dispose();
    _depositAmountController.dispose();
    _depositReferenceController.dispose();
    super.dispose();
  }

  Future<void> _createDeposit(String currency) async {
    if (_depositOutcomeUnknown && _pendingDeposit != null) {
      await _submitPendingDeposit();
      return;
    }
    _depositAmountController.text =
        _pendingDeposit?.submittedCashAmount.toString() ?? '';
    _depositReferenceController.text = _pendingDeposit?.telebirrReference ?? '';
    setState(() {
      _showDepositForm = true;
      _depositError = null;
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
        _showDepositForm = false;
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
        _showDepositForm = !parsed.outcomeUnknown;
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
  }

  @override
  Widget build(BuildContext context) {
    final localizations = AppLocalizations.of(context)!;
    final balanceAsync = ref.watch(walletBalanceProvider);
    final transactionsAsync = ref.watch(
      walletTransactionsProvider(_transactionPage),
    );
    final depositsAsync = ref.watch(walletDepositsProvider(_depositPage));

    return Scaffold(
      appBar: AppBar(
        title: Text(localizations.balanceTitle),
        actions: const [SupportShortcut()],
        bottom: TabBar(
          isScrollable: true,
          tabAlignment: TabAlignment.start,
          controller: _tabController,
          tabs: [
            Tab(text: localizations.walletTransactionsTab),
            Tab(text: localizations.walletDepositsTab),
            Tab(text: localizations.walletTransfersTab),
          ],
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
                SectionCard(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      _BalanceSummary(balance: balance),
                      const SizedBox(height: 16),
                      Text(localizations.balanceDisclaimer),
                      const SizedBox(height: 12),
                      if (_depositError != null && !_showDepositForm) ...[
                        Text(
                          _depositError!,
                          style: TextStyle(
                            color: Theme.of(context).colorScheme.error,
                          ),
                        ),
                        if (_pendingDeposit != null) ...[
                          const SizedBox(height: 8),
                          Text(
                            '${_pendingDeposit!.submittedCashAmount} ${_pendingDeposit!.currency} · ${_pendingDeposit!.telebirrReference}',
                            style: Theme.of(context).textTheme.bodySmall,
                          ),
                        ],
                        const SizedBox(height: 12),
                      ],
                      if (_showDepositForm) ...[
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
                                    onPressed: _submittingDeposit
                                        ? null
                                        : () => setState(
                                            () => _showDepositForm = false,
                                          ),
                                    child: Text(localizations.cancel),
                                  ),
                                  FilledButton(
                                    key: const ValueKey(
                                      'wallet-submit-deposit-button',
                                    ),
                                    onPressed: _submittingDeposit
                                        ? null
                                        : () => _submitDepositForm(
                                            balance.currency,
                                          ),
                                    child: _submittingDeposit
                                        ? const SizedBox(
                                            width: 18,
                                            height: 18,
                                            child: CircularProgressIndicator(
                                              strokeWidth: 2,
                                            ),
                                          )
                                        : Text(localizations.confirm),
                                  ),
                                ],
                              ),
                            ],
                          ),
                        ),
                      ] else
                        FilledButton(
                          key: const ValueKey('wallet-submit-deposit-button'),
                          onPressed: _submittingDeposit
                              ? null
                              : () => _createDeposit(balance.currency),
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
                                      : localizations.submitDeposit,
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
                SizedBox(
                  height: (MediaQuery.sizeOf(context).height * .65)
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
  WalletRecipientLookup? _recipient;
  String? _errorMessage;
  ApiError? _apiError;
  WalletTransferRequest? _pendingTransfer;
  String? _transferIdempotencyKey;
  bool _transferOutcomeUnknown = false;

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
        _recipient = null;
        _pendingTransfer = null;
        _transferIdempotencyKey = null;
        _transferOutcomeUnknown = false;
        _phoneController.clear();
        _amountController.clear();
        _noteController.clear();
      });
      widget.onCompleted();
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
                  final bucketSummary =
                      entry.transferableBalanceAfter != null ||
                          entry.promotionalBalanceAfter != null
                      ? '\n${localizations.walletTransferableBalanceLabel}: ${entry.transferableBalanceAfter ?? entry.balanceAfter} · ${localizations.walletPromotionalShortLabel}: ${entry.promotionalBalanceAfter ?? 0}'
                      : '';
                  return Padding(
                    padding: const EdgeInsets.only(bottom: 12),
                    child: SectionCard(
                      padding: const EdgeInsets.all(12),
                      child: ListTile(
                        title: Text(
                          '${entry.transactionType} • ${entry.amount} ${entry.currency}',
                        ),
                        subtitle: Text(
                          '${entry.note ?? entry.operationId}$bucketSummary',
                        ),
                        isThreeLine: bucketSummary.isNotEmpty,
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
                          '${entry.normalizedTelebirrReference}\n${entry.status}',
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

class _BalanceSummary extends StatelessWidget {
  const _BalanceSummary({required this.balance});
  final WalletBalance balance;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context)!;
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(22),
      decoration: BoxDecoration(
        gradient: const LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [Color(0xFF081D46), Color(0xFF174A89)],
        ),
        borderRadius: BorderRadius.circular(20),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const Icon(
                Icons.account_balance_wallet_outlined,
                color: Color(0xFF9ACBFF),
                size: 22,
              ),
              const SizedBox(width: 10),
              Expanded(
                child: Text(
                  l.currentBalance,
                  style: const TextStyle(
                    color: Color(0xFFDAE8FF),
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 16),
          Text(
            '${balance.totalBalance} ${balance.currency}',
            style: const TextStyle(
              fontSize: 32,
              color: Colors.white,
              fontWeight: FontWeight.w800,
              letterSpacing: -.8,
            ),
          ),
          const Padding(
            padding: EdgeInsets.symmetric(vertical: 14),
            child: Divider(color: Color(0xFF3B5A83)),
          ),
          Wrap(
            spacing: 20,
            runSpacing: 12,
            children: [
              _amount(
                l.availableBalanceLabel,
                balance.availableBalance.toString(),
              ),
              _amount(
                l.promotionalBalanceLabel,
                balance.promotionalBalance.toString(),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _amount(String label, String value) => Column(
    crossAxisAlignment: CrossAxisAlignment.start,
    mainAxisSize: MainAxisSize.min,
    children: [
      Text(
        label,
        style: const TextStyle(color: Color(0xFFDAE8FF), fontSize: 12),
      ),
      const SizedBox(height: 4),
      Text(
        '$value ${balance.currency}',
        style: const TextStyle(
          color: Colors.white,
          fontSize: 16,
          fontWeight: FontWeight.w700,
        ),
      ),
    ],
  );
}
