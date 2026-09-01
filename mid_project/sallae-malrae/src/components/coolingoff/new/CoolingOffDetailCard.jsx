import Card from "@/components/ui/Card";
import FieldLabel from "./FieldLabel";
import ImpulseSlider from "./ImpulseSlider";
import MemoField from "./MemoField";
import CoolingOffPeriodPicker from "./CoolingOffPeriodPicker";

export default function CoolingOffDetailCard() {
  return (
    <Card className="p-6 sm:p-7">
      <FieldLabel>지금 사고 싶은 마음</FieldLabel>
      <ImpulseSlider />
      <MemoField />
      <CoolingOffPeriodPicker />
    </Card>
  );
}
