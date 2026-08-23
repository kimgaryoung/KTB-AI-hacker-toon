import DemoAppShell from '../parts/DemoAppShell';
import DashboardDemo from '../parts/DashboardDemo';
import DemoModal from '../parts/DemoModal';
import DemoCursor from '../parts/DemoCursor';
import { UploadIcon, CheckIcon } from '../../components/Icons';

export default function Scene04Upload({ fileName = '홍길동_대화.txt' }) {
  return (
    <DemoAppShell
      active="dashboard"
      cursor={
        <>
          <DemoModal step={2}>
            <div>
              <div className="modal-title">대화 데이터를 올려주세요</div>
              <div className="modal-sub">카카오톡 대화 내보내기(.txt) 또는 CSV 파일을 올려주세요</div>
              <div className="dropzone intro-dropzone">
                <UploadIcon />
                <div className="dropzone-text">파일을 여기로 끌어다 놓거나 클릭하여 업로드</div>
                <div className="dropzone-sub">.txt 또는 .csv 파일 · 최대 50MB</div>
              </div>
              <p className="checkin-note" style={{ marginTop: 8 }}>
                카카오톡 채팅방 &gt; 메뉴 &gt; 대화 내보내기를 선택하면 .txt 파일로 저장할 수 있어요.
              </p>
              <div className="file-done intro-file-done">
                <CheckIcon />
                <div>
                  <div className="file-done-name">{fileName}</div>
                  <div className="file-done-size">184KB</div>
                </div>
              </div>
            </div>
          </DemoModal>
          <div className="intro-file-fly">📄 {fileName}</div>
          <DemoCursor variant="upload" />
        </>
      }
    >
      <DashboardDemo />
    </DemoAppShell>
  );
}
