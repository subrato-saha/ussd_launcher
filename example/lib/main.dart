import 'package:flutter/material.dart';
import 'package:ussd_launcher/ussd_launcher.dart';
import 'package:permission_handler/permission_handler.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      home: DefaultTabController(
        length: 2,
        child: Scaffold(
          appBar: AppBar(
            title: const Text('USSD Launcher Demo'),
            bottom: const TabBar(
              tabs: [
                Tab(text: 'Single Session'),
                Tab(text: 'Multi Session'),
              ],
            ),
          ),
          body: const TabBarView(
            children: [
              SingleSessionTab(),
              MultiSessionTab(),
            ],
          ),
        ),
      ),
    );
  }
}

class SingleSessionTab extends StatefulWidget {
  const SingleSessionTab({super.key});

  @override
  _SingleSessionTabState createState() => _SingleSessionTabState();
}

class _SingleSessionTabState extends State<SingleSessionTab> {
  final TextEditingController _controller = TextEditingController();
  String _ussdResponse = '';
  List<Map<String, dynamic>> _simCards = [];
  int? _selectedSimId;

  @override
  void initState() {
    super.initState();
    _loadSimCards();
  }

  Future<void> _loadSimCards() async {
    var status = await Permission.phone.request();
    if (status.isGranted) {
      try {
        final simCards = await UssdLauncher.getSimCards();
        // print("simCards --------------------- $simCards");
        setState(() {
          _simCards = simCards;
          if (simCards.isNotEmpty) {
            _selectedSimId = simCards[0]['subscriptionId'] as int?;
          }
        });
      } catch (e) {
        print("Erreur lors du chargement des cartes SIM: $e");
      }
    } else {
      print("Permission téléphone non accordée");
    }
  }

  Future<void> _sendUssdRequest() async {
    setState(() {
      _ussdResponse = 'Envoi de la requête USSD...';
    });

    try {
      String? response = await UssdLauncher.sendUssdRequest(
        ussdCode: _controller.text,
        subscriptionId: _selectedSimId ?? -1,
      );
      setState(() {
        _ussdResponse = response ?? 'Aucune réponse reçue';
      });
    } catch (e) {
      setState(() {
        _ussdResponse = 'Erreur: ${e.toString()}';
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(16.0),
      child: Column(
        children: [
          DropdownButton<int>(
            value: _selectedSimId,
            hint: const Text('Sélectionner SIM'),
            items: _simCards.map((sim) {
              return DropdownMenuItem<int>(
                value: sim['subscriptionId'],
                child: Text("${sim['displayName']} (${sim['carrierName']})"),
              );
            }).toList(),
            onChanged: (value) {
              setState(() {
                _selectedSimId = value;
              });
            },
          ),
          const SizedBox(height: 16),
          TextField(
            controller: _controller,
            decoration: const InputDecoration(labelText: 'Entrer le code USSD'),
          ),
          const SizedBox(height: 16),
          ElevatedButton(
            onPressed: _sendUssdRequest,
            child: const Text('Envoyer USSD'),
          ),
          const SizedBox(height: 16),
          const Text('Réponse USSD :'),
          Text(
            _ussdResponse,
            style: const TextStyle(
                color: Colors.blue, fontWeight: FontWeight.bold),
          ),
        ],
      ),
    );
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }
}

class MultiSessionTab extends StatefulWidget {
  const MultiSessionTab({super.key});

  @override
  _MultiSessionTabState createState() => _MultiSessionTabState();
}

class _MultiSessionTabState extends State<MultiSessionTab> {
  final TextEditingController _ussdController = TextEditingController();
  final List<TextEditingController> _optionControllers = [];
  String _lastUssdResponse = ''; // Dernier message USSD reçu
  bool _isLoading = false;
  List<Map<String, dynamic>> _simCards = [];
  int? _selectedSlotIndex;

  String _sessionStatus = '';

  @override
  void initState() {
    super.initState();
    _loadSimCards();

    // Configurer le listener pour les messages USSD
    UssdLauncher.setUssdMessageListener(_onUssdMessageReceived);
  }

  /// Méthode appelée lorsque un message USSD est reçu.
  void _onUssdMessageReceived(String message) {
    print("Message USSD reçu: $message"); // Journalisation
    setState(() {
      // Vérifier si c'est un message de fin de session
      if (message.contains("SESSION_COMPLETED") ||
          message.contains("completed") ||
          message.contains("cancelled")) {
        _sessionStatus = "Session USSD terminée.";
        _isLoading = false;
      } else {
        // Ne garder que le dernier message USSD significatif
        _lastUssdResponse = message;
      }
    });
  }

  Future<void> _loadSimCards() async {
    var statut = await Permission.phone.request();
    if (statut.isGranted) {
      final simCards = await UssdLauncher.getSimCards();
      setState(() {
        _simCards = simCards;
        if (simCards.isNotEmpty) {
          _selectedSlotIndex = simCards[0]['slotIndex'];
        }
      });
    } else {
      print("Permission téléphone non accordée");
    }
  }

  void _launchMultiSessionUssd() async {
    setState(() {
      _isLoading = true;
      _lastUssdResponse = ''; // Réinitialiser au début d'une nouvelle session
      _sessionStatus = '';
    });

    try {
      List<String> options =
          _optionControllers.map((controller) => controller.text).toList();

      await UssdLauncher.multisessionUssd(
        code: _ussdController.text,
        slotIndex: (_selectedSlotIndex ?? 0),
        options: options,
      );
      // Aucun besoin de gérer 'res1' ici, les messages sont reçus via le listener
    } catch (e) {
      _updateUssdMessages('\nErreur : ${e.toString()}');
    } finally {
      setState(() {
        _isLoading = false;
      });
    }
  }

  void _updateUssdMessages(String newText) {
    setState(() {
      _lastUssdResponse = newText;
    });
  }

  void _addOptionField() {
    setState(() {
      _optionControllers.add(TextEditingController());
    });
  }

  void _removeOptionField() {
    setState(() {
      if (_optionControllers.isNotEmpty) {
        _optionControllers.removeLast().dispose();
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(16.0),
      child: SingleChildScrollView(
        child: Column(
          children: [
            // Affichage en temps réel de slotIndex
            Text(
              'Slot SIM sélectionné : ${_selectedSlotIndex ?? "Aucun"}',
              style: const TextStyle(fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 16),
            DropdownButton<int>(
              value: _selectedSlotIndex,
              hint: const Text('Sélectionner SIM'),
              items: _simCards.map((sim) {
                return DropdownMenuItem<int>(
                  value: sim['slotIndex'],
                  child: Text("${sim['displayName']} (${sim['carrierName']})"),
                );
              }).toList(),
              onChanged: (value) {
                setState(() {
                  _selectedSlotIndex = value;
                });
              },
            ),
            const SizedBox(height: 16),
            TextField(
              controller: _ussdController,
              decoration:
                  const InputDecoration(labelText: 'Entrer le code USSD'),
            ),
            ..._optionControllers.asMap().entries.map((entry) {
              return Padding(
                padding: const EdgeInsets.only(top: 8.0),
                child: TextField(
                  controller: entry.value,
                  keyboardType: TextInputType.number,
                  decoration:
                      InputDecoration(labelText: 'Option ${entry.key + 1}'),
                ),
              );
            }),
            const SizedBox(height: 16),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceEvenly,
              children: [
                ElevatedButton(
                  onPressed: _addOptionField,
                  child: const Text('Ajouter Option'),
                ),
                ElevatedButton(
                  onPressed:
                      _optionControllers.isNotEmpty ? _removeOptionField : null,
                  child: const Text('Retirer Option'),
                ),
              ],
            ),
            const SizedBox(height: 16),
            ElevatedButton(
              onPressed: _isLoading ? null : _launchMultiSessionUssd,
              child: const Text('Lancer USSD Multi-Session'),
            ),
            const SizedBox(height: 16),
            const Text('Réponse USSD :',
                style: TextStyle(fontWeight: FontWeight.bold)),
            Container(
              width: double.infinity,
              constraints: const BoxConstraints(minHeight: 100),
              decoration: BoxDecoration(
                border: Border.all(color: Colors.blueAccent),
                borderRadius: BorderRadius.circular(8.0),
                color: Colors.grey[50],
              ),
              padding: const EdgeInsets.all(12.0),
              child: _lastUssdResponse.isEmpty
                  ? const Text(
                      'En attente de réponse...',
                      style: TextStyle(
                          color: Colors.grey, fontStyle: FontStyle.italic),
                    )
                  : Text(
                      _lastUssdResponse,
                      style: const TextStyle(
                        color: Colors.blue,
                        fontWeight: FontWeight.bold,
                        fontSize: 15,
                      ),
                    ),
            ),
            const SizedBox(height: 16),
            if (_sessionStatus.isNotEmpty)
              Text(
                _sessionStatus,
                style: const TextStyle(
                    color: Colors.green, fontWeight: FontWeight.bold),
              ),
          ],
        ),
      ),
    );
  }

  @override
  void dispose() {
    // Nettoyer les contrôleurs de texte
    for (var controller in _optionControllers) {
      controller.dispose();
    }
    _ussdController.dispose();
    super.dispose();
  }
}
