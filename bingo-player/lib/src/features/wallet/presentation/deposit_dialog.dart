import 'package:bingo_player/l10n/app_localizations.dart';
import 'package:bingo_player/src/core/network/api_error.dart';
import 'package:bingo_player/src/features/wallet/domain/wallet_models.dart';
import 'package:flutter/material.dart';

class DepositDialog extends StatefulWidget {
  const DepositDialog({
    super.key,
    required this.currency,
    this.initialRequest,
    this.initialApiError,
  });

  final String currency;
  final DepositRequest? initialRequest;
  final ApiError? initialApiError;

  @override
  State<DepositDialog> createState() => _DepositDialogState();
}

class _DepositDialogState extends State<DepositDialog> {
  final GlobalKey<FormState> _formKey = GlobalKey<FormState>();
  final TextEditingController _amountController = TextEditingController();
  final TextEditingController _referenceController = TextEditingController();
  bool _busy = false;
  bool _amountEdited = false;
  bool _referenceEdited = false;
  bool get _validAmount {
    final amount = num.tryParse(_amountController.text.trim());
    return amount != null && amount.isFinite && amount > 0;
  }

  bool get _validReference => _referenceController.text.trim().isNotEmpty;

  @override
  void initState() {
    super.initState();
    _amountController.text =
        widget.initialRequest?.submittedCashAmount.toString() ?? '';
    _referenceController.text = widget.initialRequest?.telebirrReference ?? '';
  }

  @override
  void dispose() {
    _amountController.dispose();
    _referenceController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) {
      return;
    }
    setState(() => _busy = true);
    final request = DepositRequest(
      submittedCashAmount: num.parse(_amountController.text.trim()),
      currency: widget.currency,
      telebirrReference: _referenceController.text.trim(),
    );
    if (!mounted) return;
    Navigator.of(context).pop(request);
  }

  @override
  Widget build(BuildContext context) {
    final localizations = AppLocalizations.of(context)!;
    final amountError = _amountEdited
        ? null
        : fieldErrorMessage(
            widget.initialApiError,
            'submittedCashAmount',
            localizations,
          );
    final referenceError = _referenceEdited
        ? null
        : fieldErrorMessage(
            widget.initialApiError,
            'telebirrReference',
            localizations,
          );
    return AlertDialog(
      scrollable: true,
      title: Text(localizations.submitDeposit),
      content: Form(
        key: _formKey,
        autovalidateMode: AutovalidateMode.onUserInteraction,
        child: ListenableBuilder(
          listenable: Listenable.merge([
            _amountController,
            _referenceController,
          ]),
          builder: (context, _) {
            final guidance = !_validAmount
                ? _amountController.text.trim().isEmpty
                      ? localizations.requiredField
                      : localizations.depositAmountPositive
                : !_validReference
                ? localizations.requiredField
                : null;
            final showGuidance =
                (!_validAmount && !_amountEdited) ||
                (_validAmount && !_validReference && !_referenceEdited);
            return Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                TextFormField(
                  key: const ValueKey('deposit-amount-field'),
                  controller: _amountController,
                  textInputAction: TextInputAction.next,
                  onFieldSubmitted: (_) => FocusScope.of(context).nextFocus(),
                  onChanged: (_) {
                    if (!_amountEdited) {
                      setState(() => _amountEdited = true);
                    }
                  },
                  keyboardType: const TextInputType.numberWithOptions(
                    decimal: true,
                  ),
                  decoration: InputDecoration(
                    labelText: localizations.amountLabel,
                    errorText: amountError,
                  ),
                  validator: (value) {
                    final text = value?.trim() ?? '';
                    if (text.isEmpty) return localizations.requiredField;
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
                  controller: _referenceController,
                  textInputAction: TextInputAction.done,
                  onFieldSubmitted: (_) => _submit(),
                  onChanged: (_) {
                    if (!_referenceEdited) {
                      setState(() => _referenceEdited = true);
                    }
                  },
                  decoration: InputDecoration(
                    labelText: localizations.telebirrReferenceLabel,
                    errorText: referenceError,
                  ),
                  validator: (value) => (value == null || value.trim().isEmpty)
                      ? localizations.requiredField
                      : null,
                ),
                if (showGuidance && guidance != null) ...[
                  const SizedBox(height: 8),
                  Semantics(
                    liveRegion: true,
                    child: Align(
                      alignment: AlignmentDirectional.centerStart,
                      child: Text(
                        guidance,
                        style: Theme.of(context).textTheme.bodySmall,
                      ),
                    ),
                  ),
                ],
              ],
            );
          },
        ),
      ),
      actions: [
        TextButton(
          onPressed: _busy ? null : () => Navigator.of(context).pop(),
          child: Text(localizations.cancel),
        ),
        ListenableBuilder(
          listenable: Listenable.merge([
            _amountController,
            _referenceController,
          ]),
          builder: (context, _) => FilledButton(
            onPressed: _busy || !_validAmount || !_validReference
                ? null
                : _submit,
            child: Text(localizations.confirm),
          ),
        ),
      ],
    );
  }
}
