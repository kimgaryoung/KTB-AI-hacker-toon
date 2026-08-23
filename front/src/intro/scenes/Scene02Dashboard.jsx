import DemoAppShell from '../parts/DemoAppShell';
import DashboardDemo from '../parts/DashboardDemo';
import DemoCursor from '../parts/DemoCursor';

export default function Scene02Dashboard() {
  return (
    <DemoAppShell active="dashboard" cursor={<DemoCursor variant="dashboard" />}>
      <DashboardDemo />
    </DemoAppShell>
  );
}
