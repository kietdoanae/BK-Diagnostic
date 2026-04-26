import Navbar from './shared/Navbar'
import Hero from './sections/Hero'
import MetricStrip from './sections/MetricStrip'
import Context from './sections/Context'
import Architecture from './sections/Architecture'
import HardwarePillar from './sections/HardwarePillar'
import MobilePillar from './sections/MobilePillar'
import WebPillar from './sections/WebPillar'
import UseCase from './sections/UseCase'
import TechStack from './sections/TechStack'
import Team from './sections/Team'
import Footer from './sections/Footer'

export default function LandingPage() {
  return (
    <div className="landing-root">
      <Navbar />
      <Hero />
      <MetricStrip />
      <Context />
      <Architecture />
      <HardwarePillar />
      <MobilePillar />
      <WebPillar />
      <UseCase />
      <TechStack />
      <Team />
      <Footer />
    </div>
  )
}
