import 'package:flutter/material.dart';
import '../../../core/native_bridge.dart';
import '../../../main.dart'; // import to MainScreen

class PermissionsScreen extends StatefulWidget {
  const PermissionsScreen({super.key});

  @override
  State<PermissionsScreen> createState() => _PermissionsScreenState();
}

class _PermissionsScreenState extends State<PermissionsScreen> with WidgetsBindingObserver {
  bool _accessibilityEnabled = false;
  bool _notificationEnabled = false;
  bool _overlayEnabled = false;
  bool _calendarEnabled = false;
  bool _isLoading = true;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _checkPermissions();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      // 앱으로 돌아왔을 때 권한 상태 다시 확인
      _checkPermissions();
    }
  }

  Future<void> _checkPermissions() async {
    setState(() => _isLoading = true);
    final permissions = await NativeBridge.checkServicesEnabled();
    
    if (mounted) {
      setState(() {
        _accessibilityEnabled = permissions['accessibility'] == true;
        _notificationEnabled = permissions['notification'] == true;
        _overlayEnabled = permissions['overlay'] == true;
        _calendarEnabled = permissions['calendar'] == true;
        _isLoading = false;
      });

      // 모든 권한이 허용된 경우 메인 화면으로 이동
      if (_allPermissionsGranted()) {
        Navigator.of(context).pushReplacement(
          MaterialPageRoute(builder: (_) => const MainScreen()),
        );
      }
    }
  }

  bool _allPermissionsGranted() {
    return _accessibilityEnabled && _notificationEnabled && _overlayEnabled && _calendarEnabled;
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('초기 권한 설정'),
        centerTitle: true,
      ),
      body: _isLoading 
          ? const Center(child: CircularProgressIndicator())
          : SingleChildScrollView(
              child: Padding(
                padding: const EdgeInsets.all(24.0),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    const Icon(Icons.security, size: 64, color: Colors.blueAccent),
                    const SizedBox(height: 24),
                    const Text(
                      '대충톡을 사용하려면\n다음 권한들이 필수적입니다.',
                      textAlign: TextAlign.center,
                      style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
                    ),
                    const SizedBox(height: 16),
                    const Text(
                      '안드로이드 정책상 아래 작업들은\n[설정 열기]를 눌러 직접 허용해주셔야 합니다.',
                      textAlign: TextAlign.center,
                      style: TextStyle(fontSize: 14, color: Colors.grey),
                    ),
                    const SizedBox(height: 32),
                    _buildPermissionItem(
                      title: '1. 접근성 권한 (Accessibility)',
                      description: '사용자의 타이핑 및 전송 이벤트를 감지합니다.',
                      isGranted: _accessibilityEnabled,
                      onOpenSettings: NativeBridge.openAccessibilitySettings,
                    ),
                    const SizedBox(height: 16),
                    _buildPermissionItem(
                      title: '2. 알림 접근 허용 (Notification)',
                      description: '수신된 채팅 메시지를 감지하고 읽어옵니다.',
                      isGranted: _notificationEnabled,
                      onOpenSettings: NativeBridge.openNotificationSettings,
                    ),
                    const SizedBox(height: 16),
                    _buildPermissionItem(
                      title: '3. 다른 앱 위에 표시 (Overlay)',
                      description: '화면 위에 AI 답장 플로팅 버튼을 띄웁니다.',
                      isGranted: _overlayEnabled,
                      onOpenSettings: NativeBridge.openOverlaySettings,
                    ),
                    const SizedBox(height: 16),
                    _buildPermissionItem(
                      title: '4. 캘린더 접근 권한 (Calendar)',
                      description: '오늘 일정을 확인하여 답변에 참고합니다.',
                      isGranted: _calendarEnabled,
                      onOpenSettings: NativeBridge.requestCalendarPermission,
                    ),
                    const SizedBox(height: 24),
                  ],
                ),
              ),
            ),
    );
  }

  Widget _buildPermissionItem({
    required String title,
    required String description,
    required bool isGranted,
    required VoidCallback onOpenSettings,
  }) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: isGranted ? Colors.green.shade50 : Colors.red.shade50,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(
          color: isGranted ? Colors.green.shade200 : Colors.red.shade200,
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(
                isGranted ? Icons.check_circle : Icons.warning_amber_rounded,
                color: isGranted ? Colors.green : Colors.red,
              ),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  title,
                  style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),
          Text(description, style: const TextStyle(fontSize: 13, color: Colors.black54)),
          if (!isGranted) ...[
            const SizedBox(height: 12),
            Align(
              alignment: Alignment.centerRight,
              child: ElevatedButton(
                onPressed: onOpenSettings,
                child: const Text('설정 열기'),
              ),
            ),
          ] else ...[
             const SizedBox(height: 12),
             const Align(
               alignment: Alignment.centerRight,
               child: Text('완료', style: TextStyle(color: Colors.green, fontWeight: FontWeight.bold)),
             )
          ]
        ],
      ),
    );
  }
}
