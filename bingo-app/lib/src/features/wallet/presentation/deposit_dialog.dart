import 'package:bingo_app/l10n/app_localizations.dart';
import 'package:bingo_app/src/features/wallet/domain/wallet_models.dart';
import 'package:flutter/material.dart';

class DepositDialog extends StatefulWidget {
  const DepositDialog({super.key, required this.currency});

  final String currency;

  @override
  State<DepositDialog> createState() => _DepositDialogState();
}

class _DepositDialogState extends State<DepositDialog> {
  final GlobalKey<FormState> _formKey = GlobalKey<FormState>();
  final TextEditingController _amountController = TextEditingController();
  final TextEditingController _referenceController = TextEditingController();
  bool _busy = false;

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
    return AlertDialog(
      title: Text(localizations.submitDeposit),
      content: Form(
        key: _formKey,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextFormField(
              controller: _amountController,
              keyboardType: const TextInputType.numberWithOptions(decimal: true),
              decoration: InputDecoration(labelText: localizations.amountLabel),
              validator: (value) => (num.tryParse(value ?? '') == null) ? localizations.requiredField : null,
            ),
            const SizedBox(height: 12),
            TextFormField(
              controller: _referenceController,
              decoration: InputDecoration(labelText: localizations.telebirrReferenceLabel),
              validator: (value) => (value == null || value.trim().isEmpty) ? localizations.requiredField : null,
            ),
          ],
        ),
      ),
      actions: [
        TextButton(onPressed: _busy ? null : () => Navigator.of(context).pop(), child: Text(localizations.cancel)),
        FilledButton(onPressed: _busy ? null : _submit, child: Text(localizations.confirm)),
      ],
    );
  }
}
