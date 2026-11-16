import SwiftUI

/// CI-only stub. Not an App Store / Screen Time product.
struct ContentView: View {
    var body: some View {
        VStack(spacing: 16) {
            Text("QuizGate")
                .font(.largeTitle.bold())
            Text("Stub build for CI. Production iOS (Family Controls) is a later track.")
                .multilineTextAlignment(.center)
                .foregroundStyle(.secondary)
                .padding()
        }
        .padding()
    }
}

#Preview {
    ContentView()
}
