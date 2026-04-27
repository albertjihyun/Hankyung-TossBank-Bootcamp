const TEST_DATA = {
  questions: [
    {
      id: 1,
      question: "갑자기 여유 자금이 생겼다면 어떻게 하시겠습니까?",
      options: [
        { text: "일단 안전한 곳에 넣어두고 생각한다.", type: "SAFE" },
        { text: "자료를 충분히 분석하고 투자한다.", type: "ANALYSIS" },
        { text: "요즘 뜨는 투자처를 찾아본다.", type: "TREND" },
        { text: "수익이 크다면 과감하게 투자한다.", type: "RISK" }
      ]
    },
    {
      id: 2,
      question: "투자를 결정할 때 가장 중요하게 보는 것은?",
      options: [
        { text: "원금 손실 가능성", type: "SAFE" },
        { text: "기업 재무와 데이터", type: "ANALYSIS" },
        { text: "시장 분위기와 흐름", type: "TREND" },
        { text: "수익률 가능성", type: "RISK" }
      ]
    },
    {
      id: 3,
      question: "주변 사람들이 특정 종목을 추천한다면?",
      options: [
        { text: "검증되지 않았으면 안 한다.", type: "SAFE" },
        { text: "데이터를 찾아보고 판단한다.", type: "ANALYSIS" },
        { text: "지금 시장 반응을 먼저 본다.", type: "TREND" },
        { text: "기회라면 빠르게 들어간다.", type: "RISK" }
      ]
    },
    {
      id: 4,
      question: "투자 손실이 발생했을 때 당신의 반응은?",
      options: [
        { text: "다음부터 더 안정적인 투자만 한다.", type: "SAFE" },
        { text: "왜 손실이 났는지 분석한다.", type: "ANALYSIS" },
        { text: "시장 분위기를 다시 확인한다.", type: "TREND" },
        { text: "손실보다 다음 기회를 본다.", type: "RISK" }
      ]
    },
    {
      id: 5,
      question: "투자를 시작할 때 가장 먼저 하는 행동은?",
      options: [
        { text: "안전한 상품부터 찾는다.", type: "SAFE" },
        { text: "리포트와 데이터를 조사한다.", type: "ANALYSIS" },
        { text: "지금 사람들이 뭘 하는지 본다.", type: "TREND" },
        { text: "수익 가능성이 큰 곳을 찾는다.", type: "RISK" }
      ]
    },
    {
      id: 6,
      question: "투자 스타일을 한 문장으로 표현하면?",
      options: [
        { text: "천천히 안정적으로.", type: "SAFE" },
        { text: "이해한 것만 투자.", type: "ANALYSIS" },
        { text: "흐름을 읽는 투자.", type: "TREND" },
        { text: "큰 수익을 노리는 투자.", type: "RISK" }
      ]
    },
    {
      id: 7,
      question: "시장 변동성이 클 때 당신은?",
      options: [
        { text: "투자를 줄이고 지켜본다.", type: "SAFE" },
        { text: "데이터를 분석한다.", type: "ANALYSIS" },
        { text: "시장의 흐름을 살핀다.", type: "TREND" },
        { text: "기회라고 생각한다.", type: "RISK" }
      ]
    },
    {
      id: 8,
      question: "투자 정보를 어디서 가장 많이 얻나요?",
      options: [
        { text: "안정적인 금융기관 정보", type: "SAFE" },
        { text: "기업 보고서와 데이터", type: "ANALYSIS" },
        { text: "뉴스와 커뮤니티", type: "TREND" },
        { text: "고수익 사례", type: "RISK" }
      ]
    },
    {
      id: 9,
      question: "투자를 할 때 가장 걱정되는 것은?",
      options: [
        { text: "원금 손실", type: "SAFE" },
        { text: "정보 부족", type: "ANALYSIS" },
        { text: "타이밍 놓침", type: "TREND" },
        { text: "큰 기회 놓침", type: "RISK" }
      ]
    },
    {
      id: 10,
      weight: 3,
      question: "딱 하나만 선택해야 한다면 당신에게 가장 중요한 투자 기준은?",
      options: [
        { text: "안정성", type: "SAFE" },
        { text: "분석과 근거", type: "ANALYSIS" },
        { text: "시장 흐름", type: "TREND" },
        { text: "높은 수익", type: "RISK" }
      ]
    }
  ],

  results: {
    SAFE: {
      title: "안정형 투자자",
      description: "안정적인 자산 관리와 리스크 최소화를 중요하게 생각하는 투자자입니다.",
      analysisPoints: [
        "원금 보전과 예측 가능한 흐름을 중요하게 생각합니다.",
        "급격한 변동보다 꾸준하고 안정적인 투자를 선호합니다.",
        "의사결정에서 손실 가능성을 먼저 고려하는 편입니다.",
        "장기적으로 마음 편한 투자 방식을 추구하는 경향이 있습니다."
      ]
    },
    ANALYSIS: {
      title: "분석형 투자자",
      description: "데이터와 근거를 바탕으로 논리적인 투자를 하는 스타일입니다.",
      analysisPoints: [
        "감보다 자료와 수치를 바탕으로 판단하려는 성향이 강합니다.",
        "기업 정보와 시장 데이터를 충분히 검토한 뒤 움직입니다.",
        "논리적으로 납득되는 투자에 더 큰 확신을 느낍니다.",
        "충분한 이해와 근거가 있어야 투자 결정을 내리는 편입니다."
      ]
    },
    TREND: {
      title: "트렌드형 투자자",
      description: "시장 흐름과 분위기를 빠르게 읽는 감각형 투자자입니다.",
      analysisPoints: [
        "시장 분위기와 화제가 되는 섹터의 흐름을 빠르게 포착합니다.",
        "현재 주목받는 분야에서 기회를 찾는 데 익숙한 편입니다.",
        "변화 속도가 빠른 시장에서도 유연하게 대응하려고 합니다.",
        "타이밍과 흐름이 투자 성과에 중요하다고 느끼는 경향이 있습니다."
      ]
    },
    RISK: {
      title: "고수익추구형 투자자",
      description: "높은 수익을 위해 리스크도 감수하는 공격적인 투자자입니다.",
      analysisPoints: [
        "높은 기대수익이 있다면 어느 정도 위험도 감수할 수 있습니다.",
        "기회가 왔을 때 빠르고 과감하게 움직이려는 성향이 있습니다.",
        "안정보다 성장성과 수익 가능성을 더 크게 보는 편입니다.",
        "변동성이 크더라도 큰 성과를 목표로 투자하려는 경향이 있습니다."
      ]
    }
  }
};
