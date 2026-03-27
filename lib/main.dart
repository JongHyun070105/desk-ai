import 'package:flutter/material.dart';
import 'package:flutter_dotenv/flutter_dotenv.dart';
import 'core/theme/app_theme.dart';
import 'features/chat/presentation/chat_room_list_screen.dart';
import 'features/settings/presentation/settings_screen.dart';
import 'features/onboarding/presentation/permissions_screen.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  try {
    await dotenv.load(fileName: ".env");
  } catch (e) {
    debugPrint("Failed to load .env file: $e");
  }
  runApp(const DaechungTalkApp());
}

class DaechungTalkApp extends StatelessWidget {
  const DaechungTalkApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: '대충톡',
      theme: AppTheme.lightTheme,
      darkTheme: AppTheme.darkTheme,
      themeMode: ThemeMode.system,
      home: const PermissionsScreen(),
    );
  }
}

/// 메인 화면 — BottomNavigationBar로 채팅 / 설정 탭 전환
class MainScreen extends StatefulWidget {
  const MainScreen({super.key});

  @override
  State<MainScreen> createState() => _MainScreenState();
}

class _MainScreenState extends State<MainScreen> {
  int _currentIndex = 0;

  final _screens = const [
    ChatRoomListScreen(),
    SettingsScreen(),
  ];

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return Scaffold(
      body: IndexedStack(
        index: _currentIndex,
        children: _screens,
      ),
      bottomNavigationBar: Container(
        decoration: BoxDecoration(
          border: Border(
            top: BorderSide(
              color: isDark ? AppColors.darkDivider : AppColors.lightDivider,
              width: 0.5,
            ),
          ),
        ),
        child: NavigationBar(
          selectedIndex: _currentIndex,
          onDestinationSelected: (index) {
            setState(() => _currentIndex = index);
          },
          backgroundColor: isDark ? AppColors.darkBg : Colors.white,
          indicatorColor: AppColors.primary.withValues(alpha: 0.12),
          height: 64,
          labelBehavior: NavigationDestinationLabelBehavior.alwaysShow,
          destinations: [
            NavigationDestination(
              icon: Icon(
                Icons.chat_bubble_outline_rounded,
                color: _currentIndex == 0 ? AppColors.primary : (isDark ? AppColors.darkTextTertiary : AppColors.lightTextTertiary),
              ),
              selectedIcon: const Icon(Icons.chat_bubble_rounded, color: AppColors.primary),
              label: '채팅',
            ),
            NavigationDestination(
              icon: Icon(
                Icons.settings_outlined,
                color: _currentIndex == 1 ? AppColors.primary : (isDark ? AppColors.darkTextTertiary : AppColors.lightTextTertiary),
              ),
              selectedIcon: const Icon(Icons.settings_rounded, color: AppColors.primary),
              label: '설정',
            ),
          ],
        ),
      ),
    );
  }
}
