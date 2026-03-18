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

  @override
  void initState() {
    super.initState();
    _checkStatus();
  }

  Future<void> _checkStatus() async {
    final enabled = await NativeBridge.checkServicesEnabled();
    setState(() {
      _isServiceEnabled = enabled;
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Auto-Me 설정'),
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
          const SizedBox(height: 16),
          const Text(
            '권한 설정',
            style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
          ),
          const SizedBox(height: 8),
          ListTile(
            title: const Text('접근성 서비스 설정'),
            subtitle: const Text('발신 메시지 학습을 위해 필요합니다.'),
            trailing: const Icon(Icons.arrow_forward_ios),
            onTap: () async {
              await NativeBridge.openAccessibilitySettings();
            },
          ),
          ListTile(
            title: const Text('알림 접근 허용'),
            subtitle: const Text('수신 메시지 학습 및 알림 파싱을 위해 필요합니다.'),
            trailing: const Icon(Icons.arrow_forward_ios),
            onTap: () async {
              await NativeBridge.openNotificationSettings();
            },
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
}

