import React from 'react';
import { View, Text, TouchableOpacity, ScrollView, StyleSheet } from 'react-native';
import { useColors } from '../../theme';

interface Tab {
  id: string;
  label: string;
  count?: number;
}

interface IntegratedTabsProps {
  tabs: Tab[];
  activeTabId: string;
  onTabChange: (tabId: string) => void;
}

export const IntegratedTabs: React.FC<IntegratedTabsProps> = ({ 
  tabs, 
  activeTabId, 
  onTabChange 
}) => {
  const colors = useColors();

  return (
    <View style={[styles.container, { borderBottomColor: colors.outlineVariant }]}>
      <ScrollView 
        horizontal 
        showsHorizontalScrollIndicator={false}
        style={styles.scroll}
        contentContainerStyle={styles.scrollContent}
      >
        <View style={styles.tabRow}>
          {tabs.map((tab) => {
            const isActive = tab.id === activeTabId;
            return (
              <TouchableOpacity
                key={tab.id}
                onPress={() => onTabChange(tab.id)}
                style={[
                  styles.tabItem,
                  { borderBottomColor: isActive ? colors.primary : 'transparent' }
                ]}
              >
                <View style={styles.labelRow}>
                  <Text
                    style={[
                      styles.tabLabel,
                      { color: isActive ? colors.primary : colors.onSurfaceVariant }
                    ]}
                  >
                    {tab.label}
                  </Text>
                  {tab.count !== undefined && (
                    <View 
                      style={[
                        styles.badge,
                        { backgroundColor: isActive ? colors.primaryContainer : colors.surfaceVariant }
                      ]}
                    >
                      <Text 
                        style={[
                          styles.badgeText,
                          { color: isActive ? colors.onPrimaryContainer : colors.onSurfaceVariant }
                        ]}
                      >
                        {tab.count}
                      </Text>
                    </View>
                  )}
                </View>
              </TouchableOpacity>
            );
          })}
        </View>
      </ScrollView>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    marginTop: 24,
    borderBottomWidth: 1,
  },
  scroll: {
    paddingHorizontal: 16,
  },
  scrollContent: {
    paddingRight: 32, // More space at the end
  },
  tabRow: {
    flexDirection: 'row',
  },
  tabItem: {
    marginRight: 24,
    paddingVertical: 8,
    borderBottomWidth: 2,
  },
  labelRow: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  tabLabel: {
    fontSize: 14,
    fontWeight: '600',
  },
  badge: {
    marginLeft: 6,
    paddingHorizontal: 6,
    paddingVertical: 2,
    borderRadius: 999,
  },
  badgeText: {
    fontSize: 10,
    fontWeight: 'bold',
  },
});
