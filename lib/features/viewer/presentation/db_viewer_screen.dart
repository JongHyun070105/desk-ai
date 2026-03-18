import 'package:flutter/material.dart';
import '../../../core/native_bridge.dart';

class DbViewerScreen extends StatefulWidget {
  const DbViewerScreen({super.key});

  @override
  State<DbViewerScreen> createState() => _DbViewerScreenState();
}

class _DbViewerScreenState extends State<DbViewerScreen> {
  int _messageCount = 0;
  List<Map<String, dynamic>> _messages = [];
  bool _isLoading = false;

  @override
  void initState() {
    super.initState();
    _loadData();
  }

  Future<void> _loadData() async {
    setState(() => _isLoading = true);
    try {
      final count = await NativeBridge.getMessageCount();
      final messages = await NativeBridge.getLatestMessages();
      setState(() {
        _messageCount = count;
        _messages = messages;
      });
    } finally {
      setState(() => _isLoading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('로컬 DB 뷰어'),
        actions: [
          IconButton(icon: const Icon(Icons.refresh), onPressed: _loadData),
        ],
      ),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator())
          : Column(
              children: [
                Container(
                  padding: const EdgeInsets.all(16),
                  color: Colors.blueGrey.shade50,
                  width: double.infinity,
                  child: Text(
                    '총 학습된 메시지: $_messageCount개 (최신 50개 표시)',
                    style: const TextStyle(fontWeight: FontWeight.bold),
                  ),
                ),
                Expanded(
                  child: _messages.isEmpty
                      ? const Center(child: Text('수집된 데이터가 없습니다.'))
                      : ListView.separated(
                          itemCount: _messages.length,
                          separatorBuilder: (_, __) => const Divider(height: 1),
                          itemBuilder: (context, index) {
                            final msg = _messages[index];
                            final isSentByMe = msg['isSentByMe'] ?? false;
                            return ListTile(
                              leading: Icon(
                                isSentByMe ? Icons.arrow_upward : Icons.arrow_downward,
                                color: isSentByMe ? Colors.blue : Colors.green,
                              ),
                              title: Text(msg['sender'] ?? 'Unknown'),
                              subtitle: Text(msg['message'] ?? ''),
                              trailing: Text(
                                _formatTime(msg['timestamp']),
                                style: const TextStyle(fontSize: 12),
                              ),
                            );
                          },
                        ),
                ),
              ],
            ),
    );
  }

  String _formatTime(dynamic timestamp) {
    if (timestamp == null) return '';
    final dt = DateTime.fromMillisecondsSinceEpoch(timestamp as int);
    return '${dt.hour}:${dt.minute.toString().padLeft(2, '0')}';
  }
}
