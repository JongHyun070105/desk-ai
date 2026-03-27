import 'package:flutter/material.dart';
import '../../../core/native_bridge.dart';
import '../../fallback/presentation/txt_import_screen.dart';
import '../../viewer/presentation/db_viewer_screen.dart';

class SettingsScreen extends StatefulWidget {
  const SettingsScreen({super.key});

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  bool _isServiceEnabled = false;
  bool _isIgnoringBattery = false;

  @override
  void initState() {
    super.initState();
    _checkStatus();
  }

  Future<void> _checkStatus() async {
    final enabledMap = await NativeBridge.checkServicesEnabled();
    final enabled = (enabledMap['accessibility'] == true) &&
                    (enabledMap['notification'] == true) &&
                    (enabledMap['overlay'] == true);
    
    final isIgnoringBattery = await NativeBridge.isIgnoringBatteryOptimizations();

    setState(() {
      _isServiceEnabled = enabled;
      _isIgnoringBattery = isIgnoringBattery;
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('대충톡 설정'),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: _checkStatus,
          )
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.all(16.0),
        children: [
          Card(
            color: _isServiceEnabled ? Colors.green.shade50 : Colors.orange.shade50,
            child: ListTile(
              leading: Icon(
                _isServiceEnabled ? Icons.check_circle : Icons.warning,
                color: _isServiceEnabled ? Colors.green : Colors.orange,
              ),
              title: Text(_isServiceEnabled ? '서비스 활성화됨' : '서비스 설정 필요'),
              subtitle: Text(_isServiceEnabled
                  ? '백그라운드에서 대화를 학습 중입니다.'
                  : '접근성 및 알림 권한을 허용해주세요.'),
            ),
          ),
          const Divider(),
          const Text(
            '성능 및 배터리 최적화',
            style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
          ),
          const SizedBox(height: 8),
          ListTile(
            title: const Text('배터리 최적화 제외'),
            subtitle: Text(_isIgnoringBattery 
                ? '최적화 제외됨 (안정적인 백그라운드 동작)' 
                : '백그라운드 유지 및 발열 감소를 위해 설정 권장'),
            trailing: Icon(
              _isIgnoringBattery ? Icons.battery_charging_full : Icons.battery_alert,
              color: _isIgnoringBattery ? Colors.blue : Colors.red,
            ),
            onTap: () async {
              await NativeBridge.requestIgnoreBatteryOptimizations();
              _checkStatus();
            },
          ),
          const Divider(),
          const Text(
            'AI 테스트 설정 (에뮬레이터)',
            style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
          ),
          const SizedBox(height: 8),
          ListTile(
            title: const Text('Gemini API Key 설정'),
            subtitle: const Text('에뮬레이터에서 Cloud API로 테스트할 때 사용합니다.'),
            trailing: const Icon(Icons.vpn_key),
            onTap: () => _showApiKeyDialog(context),
          ),
          const Divider(),
          const Text(
            '데이터 관리',
            style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
          ),
          const SizedBox(height: 8),
          ListTile(
            title: const Text('TXT 파일로 내보낸 대화 Import'),
            subtitle: const Text('기존 대화 내역을 모델 학습에 사용합니다.'),
            trailing: const Icon(Icons.upload_file),
            onTap: () {
              Navigator.push(
                context,
                MaterialPageRoute(builder: (context) => const TxtImportScreen()),
              );
            },
          ),
          ListTile(
            title: const Text('로컬 DB 뷰어'),
            subtitle: const Text('학습된 데이터를 로컬에서 확인합니다.'),
            trailing: const Icon(Icons.storage),
            onTap: () {
              Navigator.push(
                context,
                MaterialPageRoute(builder: (context) => const DbViewerScreen()),
              );
            },
          ),
        ],
      ),
    );
  }

  void _showApiKeyDialog(BuildContext context) {
    final controller = TextEditingController();
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Gemini API Key 입력'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Text('Google AI Studio에서 발급받은 API Key를 입력하세요.'),
            const SizedBox(height: 16),
            TextField(
              controller: controller,
              decoration: const InputDecoration(
                border: OutlineInputBorder(),
                hintText: 'API Key 입력',
              ),
              obscureText: true,
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('취소'),
          ),
          ElevatedButton(
            onPressed: () async {
              final key = controller.text.trim();
              if (key.isNotEmpty) {
                final success = await NativeBridge.setGeminiApiKey(key);
                if (mounted) {
                  Navigator.pop(context);
                  ScaffoldMessenger.of(context).showSnackBar(
                    SnackBar(content: Text(success ? 'API Key가 설정되었습니다.' : '설정 실패')),
                  );
                }
              }
            },
            child: const Text('저장'),
          ),
        ],
      ),
    );
  }
}

