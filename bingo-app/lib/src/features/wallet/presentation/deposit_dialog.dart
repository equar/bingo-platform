import 'package:bingo_app/l10n/app_localizations.dart';
import 'package:bingo_app/src/core/network/api_error.dart';
import 'package:bingo_app/src/features/wallet/domain/wallet_models.dart';
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
      submittedCashAmount: num.parse(_amountController.text),
      currency: widget.currency,
      telebirrReference: _referenceController.text.trim(),
    );
    if (!mounted) return;
    Navigator.of(context).pop(request);
  }

  @override
  Widget build(BuildContext context) {
    final localizations = AppLocalizations.of(context)!;
    final amountError = fieldErrorMessage(
      widget.initialApiError,
      'submittedCashAmount',
      localizations,
    );
    final referenceError = fieldErrorMessage(
      widget.initialApiError,
      'telebirrReference',
      localizations,
    );
    return AlertDialog(
      title: Text(localizations.submitDeposit),
      content: Form(
        key: _formKey,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextFormField(
              key: const ValueKey('deposit-amount-field'),
              controller: _amountController,
              keyboardType: const TextInputType.numberWithOptions(
                decimal: true,
              ),
              decoration: InputDecoration(
                labelText: localizations.amountLabel,
                errorText: amountError,
              ),
              validator: (value) => (num.tryParse(value ?? '') == null)
                  ? localizations.requiredField
                  : null,
            ),
            const SizedBox(height: 12),
            TextFormField(
              key: const ValueKey('deposit-reference-field'),
              controller: _referenceController,
              decoration: InputDecoration(
                labelText: localizations.telebirrReferenceLabel,
                errorText: referenceError,
              ),
              validator: (value) => (value == null || value.trim().isEmpty)
                  ? localizations.requiredField
                  : null,
            ),
          ],
        ),
      ),
      actions: [
        TextButton(
          onPressed: _busy ? null : () => Navigator.of(context).pop(),
          child: Text(localizations.cancel),
        ),
        FilledButton(
          onPressed: _busy ? null : _submit,
          child: Text(localizations.confirm),
        ),
      ],
    );
  }
}
