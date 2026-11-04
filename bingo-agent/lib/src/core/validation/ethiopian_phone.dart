bool isValidEthiopianPhone(String value) {
  var compact = value.trim().replaceAll(RegExp(r'[\s\-().]'), '');
  if (!RegExp(r'^\+?[0-9]+$').hasMatch(compact)) return false;
  if (compact.startsWith('+')) compact = compact.substring(1);

  String localNumber;
  if (compact.length == 12 && compact.startsWith('251')) {
    localNumber = compact.substring(3);
  } else if (compact.length == 10 && compact.startsWith('0')) {
    localNumber = compact.substring(1);
  } else if (compact.length == 9) {
    localNumber = compact;
  } else {
    return false;
  }
  return localNumber.length == 9 &&
      (localNumber.startsWith('9') || localNumber.startsWith('7'));
}
