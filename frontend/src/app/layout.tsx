import './globals.css';
import React from 'react';
import { AuthProvider } from '../context/AuthContext';
import Navbar from '../components/Navbar';
import VaultAssistant from '../components/VaultAssistant';

export const metadata = {
  title: 'MemoraAI - Preserving Memories, Strengthening Connections',
  description: 'AI-powered memory companion for personal and family memories.',
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en">
      <body>
        <AuthProvider>
          <Navbar />
          {children}
          <VaultAssistant />
        </AuthProvider>
      </body>
    </html>
  );
}
