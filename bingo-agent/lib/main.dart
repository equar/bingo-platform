import 'package:flutter/material.dart';

void main() {
  runApp(const BingoAgentApp());
}

class BingoAgentApp extends StatelessWidget {
  const BingoAgentApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: 'Bingo Agent',
      theme: ThemeData(useMaterial3: true, colorSchemeSeed: Colors.blue),
      home: const Scaffold(
        body: Center(
          child: Text('Bingo Agent scaffold'),
        ),
      ),
    );
  }
}
