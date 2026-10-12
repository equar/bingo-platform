import 'package:flutter/material.dart';

void main() {
  runApp(const BingoApp());
}

class BingoApp extends StatelessWidget {
  const BingoApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: 'Bingo App',
      theme: ThemeData(useMaterial3: true, colorSchemeSeed: Colors.green),
      home: const Scaffold(
        body: Center(
          child: Text('Bingo App scaffold'),
        ),
      ),
    );
  }
}
