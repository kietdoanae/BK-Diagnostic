import { useState, useCallback } from 'react'
import { MotionConfig } from 'framer-motion'
import Navbar from './shared/Navbar'
import ScrollProgress from './shared/ScrollProgress'
import CursorGlow from './shared/CursorGlow'
import PageLoader from './shared/PageLoader'
import SectionDivider from './shared/SectionDivider'
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
  const [loaded, setLoaded] = useState(false)
  const handleLoadComplete = useCallback(() => setLoaded(true), [])

  return (
    <MotionConfig reducedMotion="user">
      <PageLoader onComplete={handleLoadComplete} />
      <ScrollProgress />
      <CursorGlow />
      <div className="landing-root">
        <Navbar />
        <Hero />
        <MetricStrip />
        <SectionDivider variant="wave" />
        <Context />
        <SectionDivider variant="curve" />
        <Architecture />
        <SectionDivider variant="tilt" />
        <HardwarePillar />
        <SectionDivider variant="wave" flip />
        <MobilePillar />
        <SectionDivider variant="curve" />
        <WebPillar />
        <SectionDivider variant="tilt" />
        <UseCase />
        <SectionDivider variant="wave" flip />
        <TechStack />
        <SectionDivider variant="curve" flip />
        <Team />
        <Footer />
      </div>
    </MotionConfig>
  )
}