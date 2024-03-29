/**
 * 
 */
package com.lic.epgs.sa.fund.interest.core.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.collections4.map.HashedMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.lic.epgs.sa.constants.CommonConstants;
import com.lic.epgs.sa.exception.ApplicationException;
import com.lic.epgs.sa.fund.interest.core.service.FundCalculator;
import com.lic.epgs.sa.fund.interest.dto.AccountsDto;
import com.lic.epgs.sa.fund.interest.dto.FundCalcRateDetailsDto;
import com.lic.epgs.sa.fund.interest.dto.InterestFundResponseDto;
import com.lic.epgs.sa.fund.interest.dto.InterestRateDto;
import com.lic.epgs.sa.fund.master.entity.FundMgmtChargesSlabMstEntity;
import com.lic.epgs.sa.fund.master.service.FundMasterService;
import com.lic.epgs.sa.utils.CommonUtils;
import com.lic.epgs.sa.utils.DateUtils;
import com.lic.epgs.sa.utils.NumericUtils;

/**
 * @author Muruganandam
 *
 */
@Service
public class FundCalculatorEngine extends AccountingUtils implements FundCalculator {
	private Logger logger = LogManager.getLogger(getClass());

	@Autowired
	private FundMasterService fundMasterService;

	/****
	 * @author Muruganandam
	 * @implNote Calculate AIR i.e, Annual Interest Rate/Interest Amount for the
	 *           Variant V1 and V3
	 * @implNote TXN_AMT * INTEREST_RATE_YR(REKON_DAYS/NO_OF_YR_IN_YR)
	 *           TxnAmt*(reconDays/365)*(prevRate)
	 * @param dto
	 * @return
	 * @throws ApplicationException
	 */
	@Override
	public InterestRateDto calInterestAmount(InterestRateDto dto) throws ApplicationException {

		logger.info("calInterestAmount:{}", CommonConstants.LOGSTART);

		/** TXN_AMT * INTEREST_RATE_YR(REKON_DAYS/NO_OF_YR_IN_YR) */
		/** TxnAmt*(reconDays/365)*(prevRate-adjFactor) */
		final Double previouInterestRate = dto.getPreviouInterestRate();

		Long rekonD = calcRekonDays(dto.getEffectiveTxnDate(), dto.getTxnDate(),
				NumericUtils.isBooleanNotNull(dto.getIsOpeningBal()));

		/**
		 * if (DEBIT.equalsIgnoreCase(dto.getTxnType()) ||
		 * CREDIT.equalsIgnoreCase(dto.getTxnType())) { rekonD =
		 * calcRekonDaysForDebit(dto.getEffectiveTxnDate(), dto.getTxnDate(),
		 * NumericUtils.isBooleanNotNull(dto.getIsOpeningBal())); }
		 */
		double currentYrDays = noOfDaysInCurrentTxnYear(dateToLocalDate(dto.getTxnDate()));
		/** || rekonD > currentYrDays */
		if (rekonD < 0) {
			String reconCalc = "ReconDays=" + dto.getRekonDays() + "=>"
					+ DateUtils.dateToStringFormatDDMMYYYYSlash(dto.getEffectiveTxnDate()) + " - "
					+ DateUtils.dateToStringFormatDDMMYYYYSlash(dto.getTxnDate()) + ";";
			throw new ApplicationException("Reconcilation days is less than ZERO. i.e.," + reconCalc);
		}

		dto.setRekonDays(rekonD.intValue());

		dto.setCurrentYearDays((int) currentYrDays);
		double rekonDays = dto.getRekonDays();

		BigDecimal interestAmount = null;

		Double rekonDaysDiff = rekonDays / currentYrDays * 1.0;

		FundCalcRateDetailsDto calcRateDetails = new FundCalcRateDetailsDto();
		Double debitInterestRate = null;
		/**
		 * if (DEBIT.equalsIgnoreCase(dto.getTxnType())) { debitInterestRate =
		 * dto.getPreviouInterestRate() - dto.getAdjustmentFactor(); interestAmount =
		 * dto.getTxnAmount().multiply(BigDecimal.valueOf(rekonDaysDiff))
		 * .multiply(BigDecimal.valueOf(debitInterestRate));
		 * dto.setPreviouInterestRate(debitInterestRate);
		 * calcRateDetails.setRate(dto.getPreviouInterestRate() -
		 * dto.getAdjustmentFactor()); } else { debitInterestRate =
		 * dto.getPreviouInterestRate(); interestAmount =
		 * dto.getTxnAmount().multiply(BigDecimal.valueOf(rekonDaysDiff))
		 * .multiply(BigDecimal.valueOf(dto.getPreviouInterestRate()));
		 * calcRateDetails.setRate(debitInterestRate); }
		 */
		if (StringUtils.isNotBlank(dto.getBatchType()) && INTERIM_BATCH.equalsIgnoreCase(dto.getBatchType())) {
			debitInterestRate = dto.getPreviouInterestRate() + dto.getAdjustmentFactor();
		} else {
			debitInterestRate = dto.getPreviouInterestRate();
		}
		interestAmount = dto.getTxnAmount().multiply(BigDecimal.valueOf(rekonDaysDiff))
				.multiply(BigDecimal.valueOf(debitInterestRate));
		dto.setPreviouInterestRate(debitInterestRate);
		dto.setAirRate(debitInterestRate);
		calcRateDetails.setRate(dto.getPreviouInterestRate() + dto.getAdjustmentFactor());

		dto.setInterestAmount(interestAmount);

		Double gstAmount = 0.0;
		/*** interestAmount.doubleValue() * GST_RATE; */

		dto.setGstAmount(BigDecimal.valueOf(gstAmount));

		setWithdrawalAmount(dto);
		calcRateDetails.setCalcType(AIR_CALC_TYPE);
		calcRateDetails.setCalcLogic(CommonUtils.airV1V3Logic(dto));
		calcRateDetails.setCalcFormula(FORMULA_V1_V3_INTEREST);
		calcRateDetails.setRemarks(dto.getRemarks());
		calcRateDetails.setTxnAmount(dto.getTxnAmount());
		calcRateDetails.setRateRefId(dto.getRateRefId());
		calcRateDetails.setEntityType(dto.getEntityType());
		calcRateDetails.setRemarks(dto.getRemarks());
		calcRateDetails.setRate(debitInterestRate);
		dto.setPreviouInterestRate(previouInterestRate);
		calcRateDetails.setRateRefs(dto.getRateRefs());
		calcRateDetails.setResult(interestAmount);
		dto.setRateDetails(calcRateDetails);
		logger.info("calInterestAmount:{}", CommonConstants.LOGSTART);
		return dto;
	}

	/****
	 * @author Muruganandam
	 * @implNote FMC = slabValue*((1+slabRate)^(fmcReconDays/currentYearDays)-1)
	 * @param dto
	 * @return
	 * @throws ApplicationException
	 */
	@Override
	public InterestRateDto calFmcCharges(InterestRateDto dto) throws ApplicationException {
		/** TXN_AMT * INTEREST_RATE_YR(REKON_DAYS/NO_OF_YR_IN_YR) */

		double currentYrDays = noOfDaysInQurater(dto.getTxnDate());

		if (currentYrDays == 0) {
			throw new ApplicationException("No of days is ZERO for the given date:"
					+ DateUtils.dateToStringFormatDDMMYYYYSlash(dto.getTxnDate()));
		}

		/** Integer currentQurater = getQuarterMonth(LocalDate.now()); */

		/** dto.setFmcSlabRate(quarterRateById(currentQurater)); */

		BigDecimal interestAmount = dto.getTxnAmount().multiply(BigDecimal.valueOf(dto.getFmcSlabRate()));

		Long rekonD = calcRekonDaysForQuarter(dto.getTxnDate(), NumericUtils.isBooleanNotNull(dto.getIsOpeningBal()));
		dto.setRekonDays(rekonD.intValue());

		double rekonDays = dto.getRekonDays();

		Double rekonDaysDiff = Math.ceil(rekonDays / currentYrDays);
		interestAmount = interestAmount.multiply(BigDecimal.valueOf(rekonDaysDiff));
		/***
		 * .multiply(BigDecimal.valueOf(100));
		 */
		interestAmount = interestAmount.setScale(4, RoundingMode.HALF_EVEN);
		dto.setInterestAmount(interestAmount);

		/****
		 * 
		 * 
		 */
		dto.setFmcRekonDays(rekonD.intValue());

		double reconDaysByYear = dto.getFmcRekonDays() * 1.0 / currentYrDays * 1.0;

		double exponentialVal = Math.pow(1 + dto.getFmcSlabRate(), reconDaysByYear) - 1;

		BigDecimal fmcAmount = dto.getTxnAmount().multiply(BigDecimal.valueOf(exponentialVal));
		dto.setFmcAmount(fmcAmount);

		return dto;
	}

	/****
	 * @author Muruganandam
	 * @implNote Calculate the AIR interest amount for Variant V2
	 * @formula (((1+(airRate))^(quarterReconDays/currentYearDays)-1))*txnAmount
	 * @param dto
	 * @return
	 */
	@Override
	public InterestRateDto calFmcAirInterestAmount(InterestRateDto dto) {
		/** TXN_AMT * INTEREST_RATE_YR(REKON_DAYS/NO_OF_YR_IN_YR) */

		BigDecimal interestAmount = dto
				.getTxnAmount();/** .multiply(NumericUtils.doubleToBigDecimal(dto.getAirRate())) */

		/**
		 * Long rekonD = calcRekonDaysForQuarter(dto.getEffectiveTxnDate(),
		 * NumericUtils.isBooleanNotNull(dto.getIsOpeningBal()));
		 */

		Long rekonD = calcRekonDays(dto.getEffectiveTxnDate(), dto.getTxnDate(),
				NumericUtils.isBooleanNotNull(dto.getIsOpeningBal()));

		dto.setRekonDays(rekonD.intValue());

		double currentYrDays = noOfDaysInCurrentTxnYear(dateToLocalDate(dto.getTxnDate()));
		double rekonDays = dto.getRekonDays();

		dto.setCurrentYearDays((int) currentYrDays);
		Double rekonDaysDiff = rekonDays * 1.0 / currentYrDays * 1.0;
		interestAmount = interestAmount.multiply(BigDecimal.valueOf(rekonDaysDiff));
		/** interestAmount = NumericUtils.set4Scale(interestAmount); */
		dto.setInterestAmount(interestAmount);

		/****
		 * 
		 * 
		 */
		dto.setFmcRekonDays(rekonD.intValue());

		double reconDaysByYear = dto.getFmcRekonDays() * 1.0 / currentYrDays * 1.0;

		double exponentialVal = Math.pow((1 + dto.getAirRate()), reconDaysByYear) - 1;

		BigDecimal fmcAmount = dto.getTxnAmount().multiply(BigDecimal.valueOf(exponentialVal));
		dto.setAirAmount(fmcAmount);

		dto.setFmcRekonDays(rekonD.intValue());

		FundCalcRateDetailsDto calcRateDetails = new FundCalcRateDetailsDto();
		calcRateDetails.setRate(dto.getAirRate());
		calcRateDetails.setCalcLogic(CommonUtils.airV2Logic(dto));
		calcRateDetails.setTxnAmount(dto.getTxnAmount());
		calcRateDetails.setCalcType(AIR_CALC_TYPE);
		calcRateDetails.setCalcFormula(FORMULA_V2_AIR);
		calcRateDetails.setEntityType(dto.getEntityType());
		calcRateDetails.setRemarks(dto.getRemarks());
		calcRateDetails.setTxnAmount(dto.getTxnAmount());
		calcRateDetails.setRateRefId(dto.getRateRefId());
		calcRateDetails.setRateRefs(dto.getRateRefs());
		calcRateDetails.setResult(fmcAmount);
		dto.setRateDetails(calcRateDetails);
		return dto;
	}

	/****
	 * @author Muruganandam
	 * @implNote Calculate the MFR interest amount for Variant V2
	 * @formula (((1+(mfrRate))^(quarterReconDays/currentYearDays)-1))*txnAmount
	 * @param dto
	 * @return
	 */
	@Override
	public InterestRateDto calFmcMfrInterestAmount(InterestRateDto dto) {
		/** TXN_AMT * INTEREST_RATE_YR(REKON_DAYS/NO_OF_YR_IN_YR) */

		BigDecimal interestAmount = dto
				.getTxnAmount();/** .multiply(NumericUtils.doubleToBigDecimal(dto.getMfrRate())) */

		/**
		 * Long rekonD = calcRekonDaysForQuarter(dto.getEffectiveTxnDate(),
		 * NumericUtils.isBooleanNotNull(dto.getIsOpeningBal()));
		 */

		Long rekonD = calcRekonDays(dto.getEffectiveTxnDate(), dto.getTxnDate(),
				NumericUtils.isBooleanNotNull(dto.getIsOpeningBal()));

		dto.setRekonDays(rekonD.intValue());

		double currentYrDays = noOfDaysInCurrentTxnYear(dateToLocalDate(dto.getTxnDate()));
		double rekonDays = dto.getRekonDays();
		dto.setCurrentYearDays((int) currentYrDays);
		Double rekonDaysDiff = rekonDays * 1.0 / currentYrDays * 1.0;

		interestAmount = interestAmount.multiply(BigDecimal.valueOf(rekonDaysDiff));
		/** .multiply(BigDecimal.valueOf(100)); */
		interestAmount = interestAmount.setScale(2, RoundingMode.HALF_EVEN);
		dto.setInterestAmount(interestAmount);

		/****
		 * 
		 * 
		 */
		dto.setFmcRekonDays(rekonD.intValue());

		double reconDaysByYear = dto.getFmcRekonDays() * 1.0 / currentYrDays * 1.0;

		double exponentialVal = Math.pow((1 + dto.getMfrRate()), reconDaysByYear) - 1;

		BigDecimal mfrAmount = dto.getTxnAmount().multiply(BigDecimal.valueOf(exponentialVal));
		dto.setMfrAmount(mfrAmount);

		dto.setFmcRekonDays(rekonD.intValue());

		FundCalcRateDetailsDto calcRateDetails = new FundCalcRateDetailsDto();
		calcRateDetails.setRate(dto.getMfrRate());
		calcRateDetails.setCalcLogic(CommonUtils.mfrV2Logic(dto));
		calcRateDetails.setCalcType(MFR_CALC_TYPE);
		calcRateDetails.setTxnAmount(dto.getTxnAmount());
		calcRateDetails.setCalcFormula(FORMULA_V2_MFR);
		calcRateDetails.setEntityType(dto.getEntityType());
		calcRateDetails.setRemarks(dto.getRemarks());
		calcRateDetails.setTxnAmount(dto.getTxnAmount());
		calcRateDetails.setRateRefId(dto.getRateRefId());
		calcRateDetails.setRateRefs(dto.getRateRefs());
		calcRateDetails.setResult(mfrAmount);
		dto.setRateDetails(calcRateDetails);

		return dto;
	}

	/****
	 * @author Muruganandam
	 * @implNote Calculate the RA interest amount for Variant V2
	 * @formula (((1+ raRate))^(quarterReconDays/currentYearDays)-1))*txnAmount
	 * @param dto
	 * @return
	 */
	@Override
	public InterestRateDto calFmcRaInterestAmount(InterestRateDto dto) {
		/** TXN_AMT * INTEREST_RATE_YR(REKON_DAYS/NO_OF_YR_IN_YR) */
		BigDecimal interestAmount = dto
				.getTxnAmount();/** .multiply(NumericUtils.doubleToBigDecimal(dto.getRaRate())) */
		/**
		 * Long rekonD = calcRekonDaysForQuarter(dto.getEffectiveTxnDate(),
		 * NumericUtils.isBooleanNotNull(dto.getIsOpeningBal()));
		 */

		Long rekonD = calcRekonDays(dto.getEffectiveTxnDate(), dto.getTxnDate(),
				NumericUtils.isBooleanNotNull(dto.getIsOpeningBal()));

		dto.setRekonDays(rekonD.intValue());

		double currentYrDays = noOfDaysInCurrentTxnYear(dateToLocalDate(dto.getTxnDate()));
		double rekonDays = dto.getRekonDays();

		Double rekonDaysDiff = rekonDays * 1.0 / currentYrDays * 1.0;

		interestAmount = interestAmount.multiply(BigDecimal.valueOf(rekonDaysDiff));
		/** .multiply(BigDecimal.valueOf(100)); */
		interestAmount = interestAmount.setScale(2, RoundingMode.HALF_EVEN);
		dto.setInterestAmount(interestAmount);

		/****
		 * 
		 * 
		 */
		dto.setFmcRekonDays(rekonD.intValue());

		double reconDaysByYear = dto.getFmcRekonDays() * 1.0 / currentYrDays * 1.0;

		double exponentialVal = Math.pow((1 + dto.getRaRate()), reconDaysByYear) - 1;

		BigDecimal raAmount = dto.getTxnAmount().multiply(BigDecimal.valueOf(exponentialVal));
		dto.setRaAmount(raAmount);

		dto.setFmcRekonDays(rekonD.intValue());

		FundCalcRateDetailsDto calcRateDetails = new FundCalcRateDetailsDto();
		calcRateDetails.setRate(dto.getRaRate());
		calcRateDetails.setCalcLogic(CommonUtils.raV2Logic(dto));
		calcRateDetails.setCalcType(RA_CALC_TYPE);
		calcRateDetails.setTxnAmount(dto.getTxnAmount());
		calcRateDetails.setCalcFormula(FORMULA_V2_RA);
		calcRateDetails.setEntityType(dto.getEntityType());
		calcRateDetails.setRemarks(dto.getRemarks());
		calcRateDetails.setTxnAmount(dto.getTxnAmount());
		calcRateDetails.setRateRefId(dto.getRateRefId());
		calcRateDetails.setRateRefs(dto.getRateRefs());
		calcRateDetails.setResult(raAmount);
		dto.setRateDetails(calcRateDetails);

		return dto;
	}

	/***
	 * @implNote Per_Days_Charge_Amount=(Fund_charges_Amount/Closing Balance);
	 *           Calculate the FMC per rate for Member FMC
	 * @return
	 * @throws ApplicationException
	 */
	@Override
	public Double fmcPerRate(InterestRateDto interestRateDto) throws ApplicationException {
		if (interestRateDto.getFmcAmount() != null && interestRateDto.getClosingBalanceAmount() != null
		/**
		 * && interestRateDto.getClosingBalanceAmount().compareTo(BigDecimal.ZERO) > 0
		 */
		) {
			/** Double reconDays = (double) interestRateDto.getRekonDays().intValue(); */
			Double fmcAmount = interestRateDto.getFmcAmount().doubleValue();

			double fmcPerRate = fmcAmount / interestRateDto.getClosingBalanceAmount().doubleValue();

			FundCalcRateDetailsDto calcRateDetails = new FundCalcRateDetailsDto();
			calcRateDetails.setRate(interestRateDto.getRaRate());
			calcRateDetails.setCalcLogic("FMC Per RATE by Policy=" + String.format(F25_FORMAT, fmcPerRate) + "="
					+ fmcAmount + " / " + interestRateDto.getClosingBalanceAmount().doubleValue());
			calcRateDetails.setCalcType(FMC_PER_RATE);
			calcRateDetails.setTxnAmount(interestRateDto.getTxnAmount());
			calcRateDetails.setCalcFormula(FORMULA_V2_FMC_PER_DAY_CHARGE);
			calcRateDetails.setEntityType(interestRateDto.getEntityType());
			calcRateDetails.setRemarks(interestRateDto.getRemarks());
			calcRateDetails.setTxnAmount(interestRateDto.getClosingBalanceAmount());
			calcRateDetails.setRateRefId(interestRateDto.getRateRefId());
			calcRateDetails.setRateRefs(interestRateDto.getRateRefs());
			interestRateDto.setRateDetails(calcRateDetails);
			calcRateDetails.setResult(NumericUtils.doubleToBigDecimal(fmcPerRate));
			calcRateDetails.setEntityType(POLICY_ACCOUNT);
			/**
			 * return fmcAmount / interestRateDto.getClosingBalanceAmount().doubleValue() /
			 * reconDays;
			 */
			return fmcPerRate;

		}
		throw new ApplicationException("Opening Balance/FMC charge is Zero. Error in calculating FMC per rate:");
	}

	/****
	 * @author Muruganandam
	 * @param dto
	 * @implNote To calculate the FMC charges for the each member transaction by
	 *           policy FMC Per Rate
	 */
	@Override
	public void calculateFMCByPolicyRate(InterestRateDto dto) {

		Long quarterReconDays = calcRekonDaysForQuarter(dto.getTxnDate(),
				NumericUtils.isBooleanNotNull(dto.getIsOpeningBal()));

		dto.setRekonDays(quarterReconDays.intValue());

		long fmcRekonDays = calcRekonDaysForDebit(dto.getEffectiveTxnDate(), dto.getTxnDate(),
				NumericUtils.isBooleanNotNull(dto.getIsOpeningBal()));

		double fmcCharges = fmcRekonDays * dto.getTxnAmount().doubleValue() * dto.getPreviouInterestRate();
		Double gstAmount = fmcCharges * GST_RATE;

		dto.setFmcAmount(NumericUtils.doubleToBigDecimal(fmcCharges));
		dto.setGstAmount(NumericUtils.doubleToBigDecimal(gstAmount));

		StringBuilder sb = new StringBuilder();
		sb.append("memberFmcAmountFormula = fmcReconDays * memberClosingBalance * fmcAmountPerRate;");
		sb.append("Member FMC Amount=(" + fmcRekonDays + " * " + dto.getTxnAmount() + " * "
				+ dto.getPreviouInterestRate() + ");");

		FundCalcRateDetailsDto calcRateDetails = new FundCalcRateDetailsDto();
		calcRateDetails.setRate(dto.getFmcSlabRate());
		calcRateDetails.setCalcLogic(FORMULA_V2_FMC_PER_DAY_CHARGE);
		calcRateDetails.setCalcType(FMC_PER_RATE);
		calcRateDetails.setTxnAmount(dto.getTxnAmount());
		calcRateDetails.setCalcFormula(FORMULA_V2_FMC_PER_DAY_CHARGE);
		calcRateDetails.setEntityType(dto.getEntityType());
		calcRateDetails.setRemarks(dto.getRemarks());
		calcRateDetails.setTxnAmount(dto.getTxnAmount());
		calcRateDetails.setRateRefId(dto.getRateRefId());
		calcRateDetails.setRateRefs(dto.getRateRefs());
		calcRateDetails.setResult(NumericUtils.doubleToBigDecimal(fmcCharges));
		dto.setRateDetails(calcRateDetails);

		dto.setRemarks(sb.toString());
	}

	/***
	 * @author Muruganandam
	 * @implNote To calculate the average balance at policy level against each
	 *           transaction
	 * @param dto
	 * @return
	 * @throws ApplicationException
	 */
	@Override
	public InterestRateDto calcAverageBalance(InterestRateDto dto) throws ApplicationException {
		Long rekonD = calcRekonDays(dto.getTxnDate(), NumericUtils.isBooleanNotNull(dto.getIsOpeningBal()));
		if (StringUtils.isNotBlank(dto.getTxnType())
				&& (DEBIT.equalsIgnoreCase(dto.getTxnType()) || CREDIT.equalsIgnoreCase(dto.getTxnType()))) {
			rekonD = calcRekonDaysForDebit(dto.getEffectiveTxnDate(), dto.getTxnDate(),
					NumericUtils.isBooleanNotNull(dto.getIsOpeningBal()));
		}
		double currentYrDays = noOfDaysInCurrentTxnYear(dateToLocalDate(dto.getTxnDate()));
		/** || rekonD > currentYrDays **/
		if (rekonD < 0) {
			throw new ApplicationException("Reconcilation days is less than ZERO/Invalid.");
		}

		dto.setRekonDays(rekonD.intValue());

		double rekonDays = dto.getRekonDays();
		/***
		 * @formula =(ReconDays/366)*TXN_AMOUNT
		 */
		double avgBal = dto.getTxnAmount().doubleValue() * (rekonDays / currentYrDays * 1.0);
		dto.setAverageBalanceAmount(BigDecimal.valueOf(avgBal));
		FundCalcRateDetailsDto calcRateDetails = new FundCalcRateDetailsDto();
		calcRateDetails.setRate(avgBal);
		calcRateDetails.setCalcLogic("policyTransactionAmount * (financialYearDays / reconcilatioDays)");
		calcRateDetails.setCalcType("POLICY_AVERAGE_BALANCE");
		calcRateDetails.setTxnAmount(dto.getTxnAmount());
		calcRateDetails.setCalcFormula("policyTransactionAmount * (financialYearDays / reconcilatioDays)");
		calcRateDetails.setEntityType(dto.getEntityType());
		calcRateDetails.setRemarks(dto.getRemarks());
		calcRateDetails.setRateRefId(dto.getRateRefId());
		calcRateDetails.setRateRefs(dto.getRateRefs());
		calcRateDetails.setResult(NumericUtils.doubleToBigDecimal(avgBal));
		dto.setRateDetails(calcRateDetails);
		return dto;
	}

	/****
	 * @author Muruganandam
	 * @implNote To calculate the FMC charges for the given slab
	 * @formula FMC = slabValue*((1+slabRate)^(fmcReconDays/currentYearDays)-1)
	 * @formula
	 * @param dto
	 * @return
	 */
	@Override
	public BigDecimal calcFmcAmountBySlab(InterestRateDto dto) {
		/***
		 * @exp1 reconDaysByYear = fmcReconDays / noOfDaysInCurrentYear();
		 * @exp2 exponentialVal = (1+fmcRate)^(reconDaysByYear)
		 * @exp3 fmcAmount = txnAmount * ( exponentialVal - 1)
		 */

		LocalDate quarterEndDate = dateToLocalDate(
				quarterEndDate(dto.getTxnDate(), NumericUtils.isBooleanNotNull(dto.getIsOpeningBal())));

		LocalDate dateToLocalDate = dateToLocalDate(dto.getTxnDate());

		if (dateToLocalDate != null && dateToLocalDate.isAfter(quarterEndDate)) {
			dto.setTxnDate(quarterEndDate(dto.getTxnDate(), NumericUtils.isBooleanNotNull(dto.getIsOpeningBal())));
		}

		dto.setFmcRekonDays(
				(int) dateDiffInDays(dateToLocalDate(dto.getLastTxnDate()), dateToLocalDate(dto.getTxnDate())));

		double reconDaysByYear = dto.getFmcRekonDays() * 1.0
				/ noOfDaysInCurrentTxnYear(dateToLocalDate(dto.getTxnDate())) * 1.0;
		/**
		 * dto.setFmcSlabRate(1 + dto.getFmcSlabRate());
		 */

		double exponentialVal = Math.pow((1 + dto.getFmcSlabRate()), reconDaysByYear) - 1;
		dto.setCurrentYearDays(noOfDaysInCurrentTxnYear(dateToLocalDate(dto.getTxnDate())));
		BigDecimal fmcAmount = dto.getTxnAmount().multiply(BigDecimal.valueOf(exponentialVal));
		dto.setFmcAmount(fmcAmount);

		FundCalcRateDetailsDto calcRateDetailsDto = new FundCalcRateDetailsDto();
		calcRateDetailsDto.setGstRate(GST_RATE);
		calcRateDetailsDto.setCalcLogic(CommonUtils.fmcV2Logic(dto));
		calcRateDetailsDto.setFmcAmountPerRate(dto.getFmcSlabRate());
		calcRateDetailsDto.setCalcType(FMC_CALC_TYPE);
		calcRateDetailsDto.setCalcFormula(FORMULA_V2_FMC);
		calcRateDetailsDto.setTxnAmount(dto.getTxnAmount());
		calcRateDetailsDto.setRemarks(dto.getRemarks());
		calcRateDetailsDto.setRateRefId(dto.getRateRefId());
		calcRateDetailsDto.setRateRefs(dto.getRateRefs());
		calcRateDetailsDto.setResult(fmcAmount);
		dto.setRateDetails(calcRateDetailsDto);

		return fmcAmount;

	}

	/****
	 * @author Muruganandam
	 * @param dto
	 * @implNote To calculate the FMC charges for the given member with FMC rate
	 *           calculated at policy level
	 */
	@Override
	public void calcFmcAmountByPolicyForMember(InterestRateDto dto) {
		dto.setFmcRekonDays(
				(int) dateDiffInDays(dateToLocalDate(dto.getLastTxnDate()), dateToLocalDate(dto.getTxnDate())));
		/****
		 * @formula fmcReconDays*closingBal*fmcPerRate;
		 * @description Calculate the FMC charge for each member
		 */
		if (dto.getPreviouInterestRate() == null) {
			dto.setPreviouInterestRate(0.0);
		}
		StringBuilder sb = new StringBuilder();
		sb.append("FMC Recon Days=+" + dto.getFmcRekonDays() + " =("
				+ DateUtils.dateToStringFormatDDMMYYYYSlash(dto.getTxnDate()) + " - "
				+ DateUtils.dateToStringFormatDDMMYYYYSlash(dto.getLastTxnDate()) + ") ; ");
		if (dto.getFmcRekonDays() == 0) {
			dto.setFmcAmount(BigDecimal.ZERO);
			dto.setGstAmount(BigDecimal.ZERO);
			sb.append(" Member FMC Amount=" + dto.getFmcAmount() + "=" + dto.getTxnAmount() + " * "
					+ String.format(F25_FORMAT, dto.getFmcSlabRate()) + "; ");
			sb.append(" GST on FMC Amount=" + dto.getGstAmount() + "=" + dto.getFmcAmount() + " * " + GST_RATE + ";");
			sb.append(" PolicyFromDate to PolicyToDate = ( " + dto.getRemarks() + " )");
		} else {
			/**
			 * double fmcCharges = dto.getFmcRekonDays() * dto.getTxnAmount().doubleValue()
			 * dto.getFmcSlabRate().doubleValue();
			 */
			double fmcCharges = dto.getTxnAmount().doubleValue() * dto.getFmcSlabRate().doubleValue();
			Double gstAmount = fmcCharges * GST_RATE;
			dto.setFmcAmount(BigDecimal.valueOf(fmcCharges));
			dto.setGstAmount(BigDecimal.valueOf(gstAmount));

			/**
			 * sb.append(" memberFmcAmountFormula=memberClosingBalance * fmcAmountPerRate;"
			 * );
			 */
			sb.append(" Member FMC Amount=" + fmcCharges + "=" + dto.getTxnAmount() + " * "
					+ String.format(F25_FORMAT, dto.getFmcSlabRate()) + "; ");
			sb.append(" GST on FMC Amount=" + gstAmount + "=" + fmcCharges + " * " + GST_RATE + ";");
			sb.append(" PolicyFromDate to PolicyToDate = ( " + dto.getRemarks() + " )");
		}

		FundCalcRateDetailsDto calcRateDetailsDto = new FundCalcRateDetailsDto();
		calcRateDetailsDto.setGstRate(dto.getFmcSlabRate());
		calcRateDetailsDto.setCalcLogic(sb.toString());
		calcRateDetailsDto.setFmcAmountPerRate(dto.getFmcSlabRate());
		calcRateDetailsDto.setCalcType("FMC CHARGE FOR MEMBER");
		calcRateDetailsDto.setCalcFormula(FORMULA_V2_MEMBER_FMC_CALC);
		calcRateDetailsDto.setRemarks(dto.getRemarks());
		calcRateDetailsDto.setTxnAmount(dto.getTxnAmount());
		calcRateDetailsDto.setEntityType(dto.getEntityType());
		calcRateDetailsDto.setRateRefs(dto.getRateRefs());
		calcRateDetailsDto.setRateRefId(dto.getRateRefId());
		calcRateDetailsDto.setRate(dto.getFmcSlabRate());
		calcRateDetailsDto.setResult(dto.getFmcAmount());
		dto.setRateDetails(calcRateDetailsDto);
	}

	/****
	 * @author Muruganandam
	 * @implNote To calculate the Policy Account Value as splitup for Debit
	 *           Transaction
	 * @param fundResponseDto
	 */
	@Override
	public void policyAccountValueSplitUp(InterestFundResponseDto fundResponseDto) {

		BigDecimal totalInterestValue = NumericUtils.bigDecimalValid(fundResponseDto.getOpeningBalanceAmount())
				.add(NumericUtils.bigDecimalValid(fundResponseDto.getOpeningBalanceInterestAmount()))
				.add(NumericUtils.bigDecimalValid(fundResponseDto.getTotalContributionReceivedAmount()))
				.add(NumericUtils.bigDecimalValid(fundResponseDto.getTotalContributionInterestAmount()));

		BigDecimal debitAmount = fundResponseDto.getTotalDebitAmount()
				.add(fundResponseDto.getTotalDebitInterestAmount())
				.add(fundResponseDto.getTotalFmcChargeAmount())
				.add(fundResponseDto.getTotalFmcGstAmount());

		BigDecimal policyAccountValue = totalInterestValue.add(debitAmount);

		BigDecimal totalTxnAmount = NumericUtils.bigDecimalValid(fundResponseDto.getOpeningBalanceAmount())
				.add(NumericUtils.bigDecimalValid(fundResponseDto.getTotalContributionReceivedAmount()));
		/**
		 * .add(NumericUtils.bigDecimalNegative(fundResponseDto.getTotalDebitAmount()))
		 */

		BigDecimal totalInterestAmount = NumericUtils.bigDecimalValid(fundResponseDto.getOpeningBalanceInterestAmount())
				.add(NumericUtils.bigDecimalValid(fundResponseDto.getTotalContributionInterestAmount()));
		/**
		 * .add(NumericUtils.bigDecimalNegative(fundResponseDto.
		 * getTotalDebitInterestAmount()))
		 */

		fundResponseDto.setTotalTxnAmount(totalTxnAmount);
		fundResponseDto.setPolicyAccountValue(policyAccountValue);
		fundResponseDto.setTotalInterestAmount(totalInterestAmount);
	}

	/****
	 * @author Muruganandam
	 * @implNote To calculate the Policy Account Value
	 * @param fundResponseDto
	 */
	@Override
	public void policyAccountValue(InterestFundResponseDto fundResponseDto) {
		BigDecimal policyAccountValue = NumericUtils.bigDecimalValid(fundResponseDto.getOpeningBalanceAmount())
				.add(NumericUtils.bigDecimalValid(fundResponseDto.getOpeningBalanceInterestAmount()))
				.add(NumericUtils.bigDecimalValid(fundResponseDto.getTotalContributionReceivedAmount()))
				.add(NumericUtils.bigDecimalValid(fundResponseDto.getTotalContributionInterestAmount()))
				.add(fundResponseDto.getTotalDebitAmount())
				.subtract(NumericUtils.bigDecimalValid(fundResponseDto.getTotalDebitInterestAmount()))
				.subtract(NumericUtils.bigDecimalValid(fundResponseDto.getTotalFmcChargeAmount()))
				.subtract(NumericUtils.bigDecimalValid(fundResponseDto.getTotalFmcGstAmount()));

		if (policyAccountValue.compareTo(fundResponseDto.getTotalContributionReceivedAmount()
				.add(fundResponseDto.getTotalContributionReceivedAmount())) <= 0) {
			policyAccountValue = NumericUtils.bigDecimalValid(fundResponseDto.getOpeningBalanceAmount())
					.add(NumericUtils.bigDecimalValid(fundResponseDto.getOpeningBalanceInterestAmount()))
					.add(NumericUtils.bigDecimalValid(fundResponseDto.getTotalContributionReceivedAmount()))
					.add(NumericUtils.bigDecimalValid(fundResponseDto.getTotalContributionInterestAmount()));
		}

		BigDecimal totalTxnAmount = NumericUtils.bigDecimalValid(fundResponseDto.getOpeningBalanceAmount())
				.add(NumericUtils.bigDecimalValid(fundResponseDto.getTotalContributionReceivedAmount()))
				.add(fundResponseDto.getTotalDebitAmount());

		BigDecimal totalInterestAmount = NumericUtils.bigDecimalValid(fundResponseDto.getOpeningBalanceInterestAmount())
				.add(NumericUtils.bigDecimalValid(fundResponseDto.getTotalContributionInterestAmount()))
				.add(fundResponseDto.getTotalDebitInterestAmount());

		fundResponseDto.setTotalTxnAmount(totalTxnAmount);
		fundResponseDto.setPolicyAccountValue(policyAccountValue);
		fundResponseDto.setTotalInterestAmount(totalInterestAmount);
	}

	/****
	 * @author Muruganandam
	 * @implNote Calculate the FMC amount based on the slabs
	 * @param closingBalance
	 * @param applicableSlabs
	 **/
	@Override
	public void calculateFMC(AccountsDto reqDto) throws ApplicationException {
		logger.info("calculateFMC Start Opening Balance::{}", reqDto.getOpeningBalance());
		BigDecimal closingBalance = NumericUtils.bigDecimalPositiveValue(reqDto.getOpeningBalance());

		FundMgmtChargesSlabMstEntity fmcSlabCount = fundMasterService.getFMCSlabCount(reqDto.getPolicyType(),
				reqDto.getVariant(), DateUtils.convertStringToDateDDMMYYYYSlash(reqDto.getTrnxDate()),
				reqDto.getOpeningBalance(), reqDto.getPolicyNumber());

		List<FundMgmtChargesSlabMstEntity> applicableSlabs = fundMasterService.getFMCSlabs(reqDto.getPolicyType(),
				reqDto.getVariant(), DateUtils.convertStringToDateDDMMYYYYSlash(reqDto.getTrnxDate()),
				reqDto.getPolicyNumber(), fmcSlabCount.getSlab());

		applicableSlabs.sort(Comparator.comparing(FundMgmtChargesSlabMstEntity::getStartAmount));

		List<BigDecimal> calculatedFmcAmount = new ArrayList<>();
		List<InterestRateDto> slabRates = new ArrayList<>();
		List<String> slabs = new ArrayList<>();
		logger.info("calculateFMC Start2 Opening Balance::{}", reqDto.getOpeningBalance());

		if (!CommonUtils.isNonEmptyArray(applicableSlabs)) {
			throw new ApplicationException(
					"No FMC slab found for the given request(policyId,slabCode,policyType,Variant,txnDate)::("
							+ reqDto.getPolicyId() + "," + fmcSlabCount.getSlab() + "," + reqDto.getPolicyType() + ","
							+ reqDto.getVariant() + "," + reqDto.getTrnxDate() + ",");
		}
		applicableSlabs.stream().forEach(s -> logger.info("From: {}, To: {}, Slab: {}, Rate: {}", s.getStartAmount(),
				s.getEndAmount(), s.getSlab(), s.getFmcRate()));
		logger.info("applicableSlabs.size :: {}", applicableSlabs.size());
		for (int i = 0; i < applicableSlabs.size(); i++) {
			if (closingBalance.compareTo(BigDecimal.ZERO) >= 0) {
				FundMgmtChargesSlabMstEntity slabMstEntity = applicableSlabs.get(i);

				BigDecimal slabAmount = slabMstEntity.getEndAmount().subtract(slabMstEntity.getStartAmount())
						.add(BigDecimal.ONE);

				logger.info("for Loop => {}::{}::{}::{}::{}", slabMstEntity.getSlab(), slabMstEntity.getFmcRate(),
						slabMstEntity.getStartAmount(), slabMstEntity.getEndAmount(), slabAmount);

				Double rate = slabMstEntity.getFmcRate() != null ? slabMstEntity.getFmcRate() : 0.0;
				logger.info("********************START********************");
				logger.info("closingBalance.compareTo(slabAmount)>=1 :: {} => {} :: {}",
						closingBalance.compareTo(slabAmount) >= 1, closingBalance, slabAmount);
				logger.info("*********************END*******************");
				if (closingBalance.compareTo(slabAmount) >= 1) {
					logger.info("closingBalance.compareTo(slabAmount)>=1 :: {} => {} :: {}",
							closingBalance.compareTo(slabAmount) >= 1, closingBalance, slabAmount);
					closingBalance = closingBalance.subtract(slabAmount);
					logger.info("closingBalance={}", closingBalance);
					setInterestDetails(reqDto, calculatedFmcAmount, rate, slabAmount, slabRates);

				} else if (closingBalance.compareTo(slabAmount) <= 0) {
					logger.info("closingBalance.compareTo(slabAmount) <= 0 :: {} => {} :: {}",
							closingBalance.compareTo(slabAmount) <= 0, closingBalance, slabAmount);
					logger.info("closingBalance={}", closingBalance);
					setInterestDetails(reqDto, calculatedFmcAmount, rate, closingBalance, slabRates);
				} else {
					throw new ApplicationException("No FMC slab were iterated to calculate the FMC charges:");
				}
				logger.info("calculatedFmcAmount={}", calculatedFmcAmount);
				slabs.add(slabMstEntity.getStartAmount() + " - " + slabMstEntity.getEndAmount());
			}
		}
		BigDecimal totalFmcAmount = calculatedFmcAmount.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
		logger.info(
				"*********************setCalcValue -- fmcRateDetails Start ::totalFmcAmount:: {}************************",
				totalFmcAmount);
		logger.info("*************Entry Type::{}", reqDto.getTxnSubType() + "*************");
		FundCalcRateDetailsDto calcValue = setCalcValue(slabRates);
		calcValue.setRateRefId(fmcSlabCount.getSlab().longValue());
		calcValue.setPolicyNumber(reqDto.getPolicyNumber());
		calcValue.setCalcType(FMC_CALC_TYPE);
		calcValue.setTxnAmount(reqDto.getClosingBalance());
		calcValue.setEntityType(reqDto.getEntityType());
		calcValue.setFmcSlabs(slabs.stream().collect(Collectors.joining(" | ")));
		calcValue.setResult(totalFmcAmount);
		reqDto.setFmcAmount(totalFmcAmount);
		reqDto.setFundCalcRateDetails(calcValue);
		logger.info("************* calculatedFmcAmount={} *************", calculatedFmcAmount);
	}

	/****
	 * @author Muruganandam
	 * @implNote Calculate the FMC amount based on the slabs
	 * @param closingBalance
	 * @param applicableSlabs
	 **/
	@Override
	public void setInterestDetails(AccountsDto reqDto, List<BigDecimal> calculatedFmcAmount, Double rate,
			BigDecimal slabAmount, List<InterestRateDto> slabRates) {
		logger.info("****setInterestDetails::{}*****", CommonConstants.LOGSTART);
		InterestRateDto dto = new InterestRateDto();
		dto.setInterestAmount(null);
		dto.setFmcSlabRate(rate);
		dto.setTxnAmount(slabAmount);
		dto.setTxnDate(DateUtils.convertStringToDateDDMMYYYYSlash(reqDto.getTrnxDate()));

		if (NumericUtils.isBooleanNotNull(reqDto.getIsOpeningBal())) {
			dto.setLastTxnDate(localDateToDate(
					dateToLocalDate(DateUtils.convertStringToDateDDMMYYYYSlash(reqDto.getEffectiveTxnDate()))
							.minusDays(1)));
		} else {
			dto.setLastTxnDate(DateUtils.convertStringToDateDDMMYYYYSlash(reqDto.getEffectiveTxnDate()));
		}
		String lastDate = DateUtils.dateToStringFormatDDMMYYYYSlash(dto.getLastTxnDate());
		String txnDate = DateUtils.dateToStringFormatDDMMYYYYSlash(dto.getTxnDate());
		logger.info("***************************************************************************************");
		logger.info("***************Current Date - Next Date::( {} - {} ) ::{}", lastDate, txnDate, "***************");
		logger.info("***************************************************************************************");
		dto.setIsOpeningBal(reqDto.getIsOpeningBal());
		dto.setRekonDays(0);
		dto.setSlabValue(slabAmount);
		calcFmcAmountBySlab(dto);
		calculatedFmcAmount.add(dto.getFmcAmount());
		reqDto.setFmcRekonNoOfDays(dto.getFmcRekonDays());
		slabRates.add(dto);
		logger.info("****setInterestDetails::{}*****", CommonConstants.LOGEND);
	}

	public FundCalcRateDetailsDto setCalcValue(List<InterestRateDto> slabRates) {
		FundCalcRateDetailsDto calcRateDetailsDto = new FundCalcRateDetailsDto();
		logger.info("*******************Start setCalcValue Opening Balance::*******************");
		if (CommonUtils.isNonEmptyArray(slabRates)) {
			calcRateDetailsDto.setCalcFormula(FORMULA_V2_FMC);
			Map<BigDecimal, String> map = new HashedMap<>();
			List<BigDecimal> calculatedFmcAmount = new ArrayList<>();

			for (InterestRateDto dto : slabRates) {
				FundCalcRateDetailsDto rateDetails = dto.getRateDetails();
				StringBuilder sb = new StringBuilder();
				if (rateDetails != null) {
					sb.append(rateDetails.getCalcLogic());
				}
				map.put(dto.getTxnAmount(),
						NumericUtils.convertDoubleToString(dto.getFmcSlabRate()) + " | Calculation =  " + sb);
				calculatedFmcAmount.add(dto.getTxnAmount());
			}
			String str = map.entrySet().stream().map(e -> e.getKey() + ":" + e.getValue())
					.collect(Collectors.joining(" | "));
			calcRateDetailsDto.setCalcLogic(str);
			calcRateDetailsDto.setTxnAmount(calculatedFmcAmount.stream().reduce(BigDecimal.ZERO, BigDecimal::add));

			logger.info("*********************setCalcValue -- fmcRateDetails Start************************");
			logger.info("CalcFormula::{}", calcRateDetailsDto.getCalcFormula());
			logger.info("CalcLogic::{}", calcRateDetailsDto.getCalcLogic());
			logger.info("CalcType::{}", calcRateDetailsDto.getCalcType());
			logger.info("EntityType::{}", calcRateDetailsDto.getEntityType());
			logger.info("FmcSlabs::{}", calcRateDetailsDto.getFmcSlabs());
			logger.info("RateRefs::{}", calcRateDetailsDto.getRateRefs());
			logger.info("Rate::{}", calcRateDetailsDto.getRate());
			logger.info("Result::{}", calcRateDetailsDto.getResult());
			logger.info("CalcFormula::{}", calcRateDetailsDto.getResult());
			logger.info("*********************setCalcValue -- fmcRateDetails End************************");
		}
		return calcRateDetailsDto;
	}

}
