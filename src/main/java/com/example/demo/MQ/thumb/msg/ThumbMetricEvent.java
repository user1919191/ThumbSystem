package com.example.demo.MQ.thumb.msg;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
public class ThumbMetricEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;

    private ThumbEvent thumbEvent;

    private String stepStatus;

    private String parentStepStatus;

    private String msg;
}
